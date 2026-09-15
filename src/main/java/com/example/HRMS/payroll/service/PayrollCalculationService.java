package com.example.HRMS.payroll.service;

import com.example.HRMS.attendance.entity.AttendanceException;
import com.example.HRMS.attendance.entity.AttendanceExceptionType;
import com.example.HRMS.attendance.repository.AttendanceExceptionRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.compensation.entity.CompensationRecord;
import com.example.HRMS.compensation.repository.CompensationRecordRepository;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.repository.EmployeeRepository;
import com.example.HRMS.leave.entity.LeaveEntry;
import com.example.HRMS.leave.entity.LeaveEntryStatus;
import com.example.HRMS.leave.entity.LeaveEntryTreatment;
import com.example.HRMS.leave.repository.LeaveEntryRepository;
import com.example.HRMS.payroll.dto.PayrollCalculationDtos.CalculationSummaryResponse;
import com.example.HRMS.payroll.entity.PayrollDayResult;
import com.example.HRMS.payroll.entity.PayrollEmployeeResult;
import com.example.HRMS.payroll.entity.PayrollLineType;
import com.example.HRMS.payroll.entity.PayrollResultLine;
import com.example.HRMS.payroll.entity.PayrollResultStatus;
import com.example.HRMS.payroll.entity.PayrollRun;
import com.example.HRMS.payroll.entity.PayrollRunStatus;
import com.example.HRMS.payroll.repository.PayrollDayResultRepository;
import com.example.HRMS.payroll.repository.PayrollEmployeeResultRepository;
import com.example.HRMS.payroll.repository.PayrollResultLineRepository;
import com.example.HRMS.payroll.repository.PayrollRunRepository;
import com.example.HRMS.payrollinput.entity.Arrear;
import com.example.HRMS.payrollinput.entity.VariableEarning;
import com.example.HRMS.payrollinput.repository.ArrearRepository;
import com.example.HRMS.payrollinput.repository.VariableEarningRepository;
import com.example.HRMS.workcalendar.entity.WorkCalendar;
import com.example.HRMS.workcalendar.entity.WorkCalendarAssignment;
import com.example.HRMS.workcalendar.repository.WorkCalendarAssignmentRepository;
import com.example.HRMS.workcalendar.repository.WorkCalendarRepository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-statutory (pre-statutory) payroll calculation use cases (API spec §16/§17;
 * task V2-008A). Calculates and persists the pre-statutory result and moves the
 * run {@code DRAFT → CALCULATED_PRE_STATUTORY} (calculate) or replaces the result
 * set in place (recalculate).
 *
 * <p><b>Scope.</b> This slice computes the NON-statutory pipeline only:
 * eligibility → payable days → compensation state(s) → fixed prorated earnings
 * → variable earnings → arrears → gross + earning lines + day snapshot. It does
 * NOT compute PF/PT/TDS/other deductions/net pay (those columns are left NULL —
 * never fabricated {@code 0.00}, Data Model §10.2.1), creates no
 * {@code PayrollStatutoryResult} rows, runs no Health Check, and performs no
 * approval/lock/output.
 *
 * <p><b>Read-only inputs.</b> The calculation only reads Employee, Compensation,
 * LeaveEntry, AttendanceException, VariableEarning, Arrear, WorkCalendar and
 * WorkCalendarAssignment; it never mutates them (in particular
 * {@code EmployeeLeaveBalance.used_quantity} is untouched — LOP is recorded in
 * the day snapshot only).
 *
 * <p><b>Proration.</b> Calendar-day basis (Business Rules §23.1–§23.4, closed):
 * {@code dailyRate = monthlyAmount ÷ calendarDaysInMonth}; a component's monthly
 * amount is prorated by the payable calendar days that fall under the
 * compensation record effective that day. Higher precision is kept internally;
 * final employee monetary values are rounded to 2 decimals at the result
 * boundary. Variable earnings and arrears are never prorated.
 *
 * <p><b>Concurrency / atomicity.</b> Both operations take a PESSIMISTIC_WRITE
 * lock on the run row and re-check status under the lock (API §16.2); the whole
 * operation (result replacement + status/version/calculated_at update + audit)
 * is one transaction, so a failure rolls everything back and leaves the run in
 * its prior state (CALC-003).
 *
 * <p><b>Blocking inputs.</b> A missing/conflicting work-calendar assignment or
 * missing compensation covering the payroll month is a blocking calculation
 * input error: the whole calculation fails ({@code 409}) and rolls back (the
 * Health Check surfacing of these is a later slice; V2-008A never half-persists).
 */
@Service
public class PayrollCalculationService {

    /** Reserved sentinel meaning "not yet calculated" (Data Model 10.1). */
    private static final String NOT_CALCULATED_VERSION = "0";
    private static final int MONEY_SCALE = 2;
    /** Internal precision for daily-rate intermediates before boundary rounding. */
    private static final int INTERNAL_SCALE = 10;

    private static final String COMPONENT_BASIC = "BASIC";
    private static final String COMPONENT_HRA = "HRA";
    private static final String COMPONENT_OTHER_FIXED = "OTHER_FIXED_ALLOWANCES";
    private static final String SOURCE_COMPENSATION = "compensation_record";
    private static final String SOURCE_VARIABLE_EARNING = "variable_earning";
    private static final String SOURCE_ARREAR = "arrear";

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollEmployeeResultRepository employeeResultRepository;
    private final PayrollResultLineRepository resultLineRepository;
    private final PayrollDayResultRepository dayResultRepository;
    private final EmployeeRepository employeeRepository;
    private final CompensationRecordRepository compensationRepository;
    private final WorkCalendarAssignmentRepository assignmentRepository;
    private final WorkCalendarRepository workCalendarRepository;
    private final LeaveEntryRepository leaveEntryRepository;
    private final AttendanceExceptionRepository attendanceExceptionRepository;
    private final VariableEarningRepository variableEarningRepository;
    private final ArrearRepository arrearRepository;
    private final LegalEntityService legalEntityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PayrollCalculationService(PayrollRunRepository payrollRunRepository,
                                     PayrollEmployeeResultRepository employeeResultRepository,
                                     PayrollResultLineRepository resultLineRepository,
                                     PayrollDayResultRepository dayResultRepository,
                                     EmployeeRepository employeeRepository,
                                     CompensationRecordRepository compensationRepository,
                                     WorkCalendarAssignmentRepository assignmentRepository,
                                     WorkCalendarRepository workCalendarRepository,
                                     LeaveEntryRepository leaveEntryRepository,
                                     AttendanceExceptionRepository attendanceExceptionRepository,
                                     VariableEarningRepository variableEarningRepository,
                                     ArrearRepository arrearRepository,
                                     LegalEntityService legalEntityService,
                                     AuditService auditService,
                                     ObjectMapper objectMapper) {
        this.payrollRunRepository = payrollRunRepository;
        this.employeeResultRepository = employeeResultRepository;
        this.resultLineRepository = resultLineRepository;
        this.dayResultRepository = dayResultRepository;
        this.employeeRepository = employeeRepository;
        this.compensationRepository = compensationRepository;
        this.assignmentRepository = assignmentRepository;
        this.workCalendarRepository = workCalendarRepository;
        this.leaveEntryRepository = leaveEntryRepository;
        this.attendanceExceptionRepository = attendanceExceptionRepository;
        this.variableEarningRepository = variableEarningRepository;
        this.arrearRepository = arrearRepository;
        this.legalEntityService = legalEntityService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * First non-statutory calculation of a {@code DRAFT} run. Locks the run,
     * re-checks it is {@code DRAFT} under the lock, computes and persists the
     * pre-statutory result set, sets calculation_version to {@code "1"},
     * calculated_at to now, transitions to {@code CALCULATED_PRE_STATUTORY}, and
     * audits — atomically.
     */
    @Transactional
    public CalculationSummaryResponse calculate(AuthenticatedUser actor, UUID runId) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        PayrollRun run = lockRun(runId, legalEntity.getId());
        if (run.getStatus() != PayrollRunStatus.DRAFT) {
            throw ApiException.conflict(ApiMessages.PAYROLL_RUN_NOT_CALCULABLE);
        }

        int count = computeAndPersist(run);

        run.setCalculationVersion(nextVersion(run.getCalculationVersion()));
        run.setCalculatedAt(LocalDateTime.now());
        run.setStatus(PayrollRunStatus.CALCULATED_PRE_STATUTORY);
        payrollRunRepository.save(run);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.PAYROLL_CALCULATED,
                AuditActions.ENTITY_PAYROLL_RUN, run.getId(), "SUCCESS", null, null));

        return summary(run, count);
    }

    /**
     * Recalculate a run that already has a pre-statutory result. Locks the run,
     * re-checks it is {@code CALCULATED_PRE_STATUTORY} under the lock, replaces
     * the entire result set, advances calculation_version, updates calculated_at,
     * stays {@code CALCULATED_PRE_STATUTORY}, and audits — atomically. Uses the
     * run's own {@code rule_version_set_id} (unchanged here).
     */
    @Transactional
    public CalculationSummaryResponse recalculate(AuthenticatedUser actor, UUID runId) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        PayrollRun run = lockRun(runId, legalEntity.getId());
        if (run.getStatus() != PayrollRunStatus.CALCULATED_PRE_STATUTORY) {
            throw ApiException.conflict(ApiMessages.PAYROLL_RUN_NOT_RECALCULABLE);
        }

        deleteExistingResults(run.getId());
        int count = computeAndPersist(run);

        run.setCalculationVersion(nextVersion(run.getCalculationVersion()));
        run.setCalculatedAt(LocalDateTime.now());
        // status stays CALCULATED_PRE_STATUTORY
        payrollRunRepository.save(run);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.PAYROLL_RECALCULATED,
                AuditActions.ENTITY_PAYROLL_RUN, run.getId(), "SUCCESS", null, null));

        return summary(run, count);
    }

    // ---- result reads -----------------------------------------------------

    /**
     * Paginated employee result summaries for a run's current calculation
     * (API §16.1). A {@code DRAFT} run has no results yet and yields an empty
     * page (not an error). Deterministic order by business Employee ID then
     * result id is applied by the query; bank details are never exposed.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<
            com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultSummary>
            listEmployeeResults(AuthenticatedUser actor, UUID runId,
                                org.springframework.data.domain.Pageable pageable) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        PayrollRun run = payrollRunRepository
                .findByIdAndLegalEntityId(runId, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.PAYROLL_RUN_NOT_FOUND));

        org.springframework.data.domain.Page<PayrollEmployeeResult> page =
                employeeResultRepository.findByPayrollRunIdOrderedByBusinessId(
                        run.getId(), pageable);
        java.util.Map<UUID, Employee> employeesById = loadEmployees(page.getContent());
        return page.map(r -> toSummary(r, employeesById.get(r.getEmployeeId())));
    }

    /**
     * One employee's full payroll result detail for a run's current calculation
     * (API §16.1): header, earning lines and day snapshot. {@code 404} if the run
     * or the employee's result is not in scope. Bank details are never exposed.
     */
    @Transactional(readOnly = true)
    public com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultDetail
            getEmployeeResult(AuthenticatedUser actor, UUID runId, UUID employeeId) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        PayrollRun run = payrollRunRepository
                .findByIdAndLegalEntityId(runId, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.PAYROLL_RUN_NOT_FOUND));

        PayrollEmployeeResult result = employeeResultRepository
                .findByPayrollRunIdAndEmployeeId(run.getId(), employeeId)
                .orElseThrow(() ->
                        ApiException.notFound(ApiMessages.PAYROLL_EMPLOYEE_RESULT_NOT_FOUND));
        Employee employee = employeeRepository.findById(result.getEmployeeId()).orElse(null);

        List<PayrollResultLine> lines = resultLineRepository
                .findByPayrollEmployeeResultIdOrderByComponentCodeAsc(result.getId());
        List<PayrollDayResult> days = dayResultRepository
                .findByPayrollEmployeeResultIdOrderByWorkDateAsc(result.getId());
        return toDetail(result, employee, lines, days);
    }

    private java.util.Map<UUID, Employee> loadEmployees(List<PayrollEmployeeResult> results) {
        List<UUID> ids = results.stream().map(PayrollEmployeeResult::getEmployeeId).toList();
        java.util.Map<UUID, Employee> byId = new LinkedHashMap<>();
        for (Employee e : employeeRepository.findAllById(ids)) {
            byId.put(e.getId(), e);
        }
        return byId;
    }

    private com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultSummary
            toSummary(PayrollEmployeeResult r, Employee e) {
        return new com.example.HRMS.payroll.dto.PayrollCalculationDtos
                .PayrollEmployeeResultSummary(
                r.getId(), r.getEmployeeId(),
                e == null ? null : e.getEmployeeId(), e == null ? null : e.getFullName(),
                r.getGrossEarnings(), r.getPfEmployee(), r.getPfEmployer(), r.getPt(), r.getTds(),
                r.getOtherDeductions(), r.getNetPay(), r.getEligibleCalendarDays(), r.getLopDays(),
                r.getPayableCalendarDays(), r.getResultStatus().name());
    }

    private com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultDetail
            toDetail(PayrollEmployeeResult r, Employee e, List<PayrollResultLine> lines,
                     List<PayrollDayResult> days) {
        List<com.example.HRMS.payroll.dto.PayrollCalculationDtos.ResultLineResponse> lineDtos =
                lines.stream().map(l ->
                        new com.example.HRMS.payroll.dto.PayrollCalculationDtos.ResultLineResponse(
                                l.getId(), l.getLineType().name(), l.getComponentCode(),
                                l.getComponentName(), l.getAmount(), l.getCalculationBasis(),
                                l.getSourceRecordType(), l.getSourceRecordId())).toList();
        List<com.example.HRMS.payroll.dto.PayrollCalculationDtos.DayResultResponse> dayDtos =
                days.stream().map(d ->
                        new com.example.HRMS.payroll.dto.PayrollCalculationDtos.DayResultResponse(
                                d.getId(), d.getWorkDate(), d.isEmploymentEligible(),
                                d.isScheduledWorkingDay(),
                                d.getLeaveTreatment() == null ? null : d.getLeaveTreatment().name(),
                                d.getLeaveQuantity(),
                                d.getAttendanceException() == null ? null
                                        : d.getAttendanceException().name(),
                                d.getLopQuantity(), d.getCompensationRecordId(),
                                d.getPayableDayQuantity())).toList();
        return new com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultDetail(
                r.getId(), r.getPayrollRunId(), r.getEmployeeId(),
                e == null ? null : e.getEmployeeId(), e == null ? null : e.getFullName(),
                r.getGrossEarnings(), r.getPfEmployee(), r.getPfEmployer(), r.getPt(), r.getTds(),
                r.getOtherDeductions(), r.getNetPay(), r.getEligibleCalendarDays(), r.getLopDays(),
                r.getPayableCalendarDays(), r.isManualTds(), r.getCalculationExplanation(),
                r.getResultStatus().name(), lineDtos, dayDtos);
    }

    // ---- core calculation -------------------------------------------------

    /**
     * Compute and persist the pre-statutory result for every eligible employee
     * in the run's legal entity for its payroll month. Returns the number of
     * employee results created.
     */
    private int computeAndPersist(PayrollRun run) {
        LocalDate monthStart = run.getPayrollMonth().withDayOfMonth(1);
        LocalDate monthEnd = run.getPayrollMonth()
                .withDayOfMonth(run.getPayrollMonth().lengthOfMonth());
        int calendarDaysInMonth = run.getPayrollMonth().lengthOfMonth();

        List<Employee> employees = employeeRepository.findEligibleForPayrollMonth(
                run.getLegalEntityId(), monthStart, monthEnd);

        int count = 0;
        for (Employee employee : employees) {
            calculateEmployee(run, employee, monthStart, monthEnd, calendarDaysInMonth);
            count++;
        }
        return count;
    }

    private void calculateEmployee(PayrollRun run, Employee employee, LocalDate monthStart,
                                   LocalDate monthEnd, int calendarDaysInMonth) {
        List<WorkCalendarAssignment> assignments =
                assignmentRepository.findByEmployeeIdOrderByEffectiveFromAsc(employee.getId());
        List<CompensationRecord> compensations =
                compensationRepository.findByEmployeeIdOrderByEffectiveFromDesc(employee.getId());
        List<LeaveEntry> leaves = leaveEntryRepository.findByEmployeeIdAndStatus(
                employee.getId(), LeaveEntryStatus.RECORDED);
        List<AttendanceException> exceptions =
                attendanceExceptionRepository.findByEmployeeId(employee.getId());
        List<VariableEarning> variableEarnings =
                variableEarningRepository.findByEmployeeIdAndPayrollRunId(
                        employee.getId(), run.getId());
        List<Arrear> arrears = arrearRepository.findByEmployeeIdAndPayrollRunId(
                employee.getId(), run.getId());

        UUID resultId = UUID.randomUUID();

        // Accumulators (full precision until boundary rounding).
        BigDecimal eligibleDays = BigDecimal.ZERO;
        BigDecimal lopDays = BigDecimal.ZERO;
        BigDecimal payableDays = BigDecimal.ZERO;
        BigDecimal proratedBasic = BigDecimal.ZERO;
        BigDecimal proratedHra = BigDecimal.ZERO;
        BigDecimal proratedOther = BigDecimal.ZERO;

        List<PayrollDayResult> dayResults = new ArrayList<>();

        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            boolean employmentEligible = isEmploymentEligible(employee, date);
            boolean scheduled = employmentEligible && isScheduledWorkingDay(
                    resolveCalendar(assignments, date, employee), date);

            LeaveEntry leave = employmentEligible ? findLeave(leaves, date) : null;
            AttendanceException exception = employmentEligible ? findException(exceptions, date)
                    : null;
            // Leave and attendance exceptions for the same day cannot silently
            // coexist (Business Rules §4.2) — an invalid/missing required
            // calculation input (API §16.1 → 400 BAD_REQUEST, not a state 409).
            if (leave != null && exception != null) {
                throw ApiException.badRequest(ApiMessages.PAYROLL_CALC_LEAVE_ATTENDANCE_CONFLICT);
            }

            BigDecimal lopQuantity = BigDecimal.ZERO;
            BigDecimal payableQuantity = BigDecimal.ZERO;
            CompensationRecord comp = null;

            if (scheduled) {
                comp = resolveCompensation(compensations, date);
                if (comp == null) {
                    // Missing required earning input (API §16.1 → 400 BAD_REQUEST).
                    throw ApiException.badRequest(ApiMessages.PAYROLL_CALC_MISSING_COMPENSATION);
                }
                eligibleDays = eligibleDays.add(BigDecimal.ONE);
                lopQuantity = lopForDay(leave, exception);
                payableQuantity = BigDecimal.ONE.subtract(lopQuantity);

                lopDays = lopDays.add(lopQuantity);
                payableDays = payableDays.add(payableQuantity);

                if (payableQuantity.signum() > 0) {
                    BigDecimal basicDaily = dailyRate(comp.getBasicMonthly(), calendarDaysInMonth);
                    BigDecimal hraDaily = dailyRate(comp.getHraMonthly(), calendarDaysInMonth);
                    BigDecimal otherDaily = dailyRate(
                            comp.getOtherFixedAllowancesMonthly(), calendarDaysInMonth);
                    proratedBasic = proratedBasic.add(basicDaily.multiply(payableQuantity));
                    proratedHra = proratedHra.add(hraDaily.multiply(payableQuantity));
                    proratedOther = proratedOther.add(otherDaily.multiply(payableQuantity));
                }
            }

            PayrollDayResult day = new PayrollDayResult();
            day.setId(UUID.randomUUID());
            day.setPayrollEmployeeResultId(resultId);
            day.setWorkDate(date);
            day.setEmploymentEligible(employmentEligible);
            day.setScheduledWorkingDay(scheduled);
            day.setLeaveTreatment(leave == null ? null : leave.getTreatment());
            day.setLeaveQuantity(leave == null ? null : leave.getQuantity());
            day.setAttendanceException(exception == null ? null : exception.getExceptionType());
            day.setLopQuantity(lopQuantity);
            day.setCompensationRecordId(comp == null ? null : comp.getId());
            day.setPayableDayQuantity(payableQuantity);
            dayResults.add(day);
        }

        // Round each fixed component at the result boundary, then build lines.
        BigDecimal basicAmount = round(proratedBasic);
        BigDecimal hraAmount = round(proratedHra);
        BigDecimal otherAmount = round(proratedOther);

        List<PayrollResultLine> lines = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;

        gross = gross.add(addFixedLine(lines, resultId, COMPONENT_BASIC, "Basic",
                basicAmount, payableDays, calendarDaysInMonth));
        gross = gross.add(addFixedLine(lines, resultId, COMPONENT_HRA, "HRA",
                hraAmount, payableDays, calendarDaysInMonth));
        gross = gross.add(addFixedLine(lines, resultId, COMPONENT_OTHER_FIXED,
                "Other Fixed Allowances", otherAmount, payableDays, calendarDaysInMonth));

        for (VariableEarning ve : variableEarnings) {
            BigDecimal amount = round(ve.getAmount());
            lines.add(earningLine(resultId, "VARIABLE_" + shortId(ve.getId()), ve.getDescription(),
                    amount, basisJson(Map.of("type", "variable_earning", "amount", amount)),
                    SOURCE_VARIABLE_EARNING, ve.getId()));
            gross = gross.add(amount);
        }
        for (Arrear arrear : arrears) {
            BigDecimal amount = round(arrear.getAmount());
            lines.add(earningLine(resultId, "ARREAR_" + shortId(arrear.getId()),
                    "Arrear (" + arrear.getPeriodReference() + ")", amount,
                    basisJson(Map.of("type", "arrear", "amount", amount,
                            "periodReference", arrear.getPeriodReference())),
                    SOURCE_ARREAR, arrear.getId()));
            gross = gross.add(amount);
        }

        PayrollEmployeeResult result = new PayrollEmployeeResult();
        result.setId(resultId);
        result.setPayrollRunId(run.getId());
        result.setEmployeeId(employee.getId());
        result.setGrossEarnings(round(gross));
        // Statutory + net columns intentionally left NULL (pre-statutory stage).
        result.setEligibleCalendarDays(roundDays(eligibleDays));
        result.setLopDays(roundDays(lopDays));
        result.setPayableCalendarDays(roundDays(payableDays));
        result.setManualTds(false);
        result.setCalculationExplanation(explanationJson(run, eligibleDays, lopDays, payableDays,
                calendarDaysInMonth, basicAmount, hraAmount, otherAmount, gross));
        result.setResultStatus(PayrollResultStatus.VALID);
        result.setCreatedAt(LocalDateTime.now());

        employeeResultRepository.save(result);
        resultLineRepository.saveAll(lines);
        dayResultRepository.saveAll(dayResults);
    }

    /** Add a fixed prorated earning line if non-zero; returns the amount added to gross. */
    private BigDecimal addFixedLine(List<PayrollResultLine> lines, UUID resultId, String code,
                                    String name, BigDecimal amount, BigDecimal payableDays,
                                    int calendarDaysInMonth) {
        if (amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        String basis = basisJson(new LinkedHashMap<>(Map.of(
                "type", "fixed_prorated",
                "component", code,
                "payableCalendarDays", payableDays.stripTrailingZeros().toPlainString(),
                "calendarDaysInMonth", String.valueOf(calendarDaysInMonth),
                "amount", amount.toPlainString())));
        lines.add(earningLine(resultId, code, name, amount, basis, SOURCE_COMPENSATION, null));
        return amount;
    }

    private PayrollResultLine earningLine(UUID resultId, String code, String name,
                                          BigDecimal amount, String basis, String sourceType,
                                          UUID sourceId) {
        PayrollResultLine line = new PayrollResultLine();
        line.setId(UUID.randomUUID());
        line.setPayrollEmployeeResultId(resultId);
        line.setLineType(PayrollLineType.EARNING);
        line.setComponentCode(code);
        line.setComponentName(name);
        line.setAmount(amount);
        line.setCalculationBasis(basis);
        line.setSourceRecordType(sourceType);
        line.setSourceRecordId(sourceId);
        return line;
    }

    // ---- day-level determination ------------------------------------------

    private boolean isEmploymentEligible(Employee employee, LocalDate date) {
        if (date.isBefore(employee.getJoiningDate())) {
            return false;
        }
        return employee.getExitDate() == null || !date.isAfter(employee.getExitDate());
    }

    /**
     * Resolve the work calendar effective on a date via the employee's
     * assignments. A missing assignment covering the date, or a resolved
     * assignment whose calendar cannot be loaded in the employee's legal entity,
     * is a blocking calculation input error.
     */
    private WorkCalendar resolveCalendar(List<WorkCalendarAssignment> assignments, LocalDate date,
                                         Employee employee) {
        WorkCalendarAssignment match = null;
        for (WorkCalendarAssignment a : assignments) {
            boolean fromOk = !date.isBefore(a.getEffectiveFrom());
            boolean toOk = a.getEffectiveTo() == null || !date.isAfter(a.getEffectiveTo());
            if (fromOk && toOk) {
                if (match != null) {
                    // Overlapping/conflicting assignments for the same date — an
                    // invalid required calculation input (API §16.1 → 400).
                    throw ApiException.badRequest(ApiMessages.PAYROLL_CALC_MISSING_WORK_CALENDAR);
                }
                match = a;
            }
        }
        if (match == null) {
            // Missing required calculation input (API §16.1 → 400 BAD_REQUEST).
            throw ApiException.badRequest(ApiMessages.PAYROLL_CALC_MISSING_WORK_CALENDAR);
        }
        return workCalendarRepository
                .findByIdAndLegalEntityId(match.getWorkCalendarId(), employee.getLegalEntityId())
                .orElseThrow(() ->
                        ApiException.badRequest(ApiMessages.PAYROLL_CALC_MISSING_WORK_CALENDAR));
    }

    /**
     * Whether the date is a scheduled working day per the calendar's flags.
     * v0 supports the standard 5-day pattern (Mon–Fri worked, Sat/Sun weekly
     * off); the flags are consumed rather than hardcoding Mon–Fri.
     */
    private boolean isScheduledWorkingDay(WorkCalendar calendar, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        if (weekend) {
            return !calendar.isSaturdaySundayWeeklyOff();
        }
        return calendar.isMondayToFriday();
    }

    private LeaveEntry findLeave(List<LeaveEntry> leaves, LocalDate date) {
        for (LeaveEntry l : leaves) {
            if (l.getLeaveDate().equals(date)) {
                return l;
            }
        }
        return null;
    }

    private AttendanceException findException(List<AttendanceException> exceptions, LocalDate date) {
        for (AttendanceException e : exceptions) {
            if (e.getAttendanceDate().equals(date)) {
                return e;
            }
        }
        return null;
    }

    /**
     * LOP quantity for a scheduled day (0, 0.5 or 1.0). Paid leave never
     * produces LOP; unpaid-LOP leave contributes its quantity; attendance
     * exceptions contribute per type (full-day absence and LOP → full day;
     * half-day → 0.5). Precedence: leave then attendance (they never coexist).
     */
    private BigDecimal lopForDay(LeaveEntry leave, AttendanceException exception) {
        if (leave != null) {
            if (leave.getTreatment() == LeaveEntryTreatment.UNPAID_LOP_LEAVE) {
                return clampDay(leave.getQuantity());
            }
            return BigDecimal.ZERO; // PAID_LEAVE: no LOP for the day
        }
        if (exception != null) {
            return switch (exception.getExceptionType()) {
                // Full-day absence is a non-payable full day.
                case FULL_DAY_ABSENCE -> BigDecimal.ONE;
                // Half-day exception is a 0.5 non-payable portion.
                case HALF_DAY -> new BigDecimal("0.5");
                // Explicit LOP carries its own quantity (0.5 or 1.0), clamped to a day.
                case LOP -> clampDay(exception.getQuantity());
            };
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal clampDay(BigDecimal quantity) {
        if (quantity == null) {
            return BigDecimal.ONE;
        }
        if (quantity.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return quantity;
    }

    /**
     * Compensation record effective on a date: the most recent record whose
     * effective_from ≤ date and (effective_to is null or ≥ date). Records are
     * pre-sorted effective_from descending, so the first match wins.
     */
    private CompensationRecord resolveCompensation(List<CompensationRecord> compensations,
                                                   LocalDate date) {
        for (CompensationRecord c : compensations) {
            boolean fromOk = !date.isBefore(c.getEffectiveFrom());
            boolean toOk = c.getEffectiveTo() == null || !date.isAfter(c.getEffectiveTo());
            if (fromOk && toOk) {
                return c;
            }
        }
        return null;
    }

    // ---- helpers ----------------------------------------------------------

    private BigDecimal dailyRate(BigDecimal monthlyAmount, int calendarDaysInMonth) {
        return monthlyAmount.divide(BigDecimal.valueOf(calendarDaysInMonth), INTERNAL_SCALE,
                RoundingMode.HALF_UP);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal roundDays(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private PayrollRun lockRun(UUID runId, UUID legalEntityId) {
        return payrollRunRepository.findByIdAndLegalEntityIdForUpdate(runId, legalEntityId)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.PAYROLL_RUN_NOT_FOUND));
    }

    private void deleteExistingResults(UUID runId) {
        List<PayrollEmployeeResult> existing =
                employeeResultRepository.findByPayrollRunId(runId);
        for (PayrollEmployeeResult result : existing) {
            dayResultRepository.deleteByPayrollEmployeeResultId(result.getId());
            resultLineRepository.deleteByPayrollEmployeeResultId(result.getId());
        }
        employeeResultRepository.deleteAll(existing);
        // Flush so the re-insert cannot collide with the unique (run, employee)
        // constraint on the rows just removed.
        employeeResultRepository.flush();
    }

    /** Advance the numeric calculation version ("0" → "1" → "2" ...). */
    private String nextVersion(String current) {
        if (current == null || NOT_CALCULATED_VERSION.equals(current)) {
            return "1";
        }
        try {
            return String.valueOf(Long.parseLong(current) + 1);
        } catch (NumberFormatException ex) {
            // Non-numeric prior version is unexpected in v0; fail rather than guess.
            throw ApiException.conflict(ApiMessages.PAYROLL_RUN_NOT_RECALCULABLE);
        }
    }

    private CalculationSummaryResponse summary(PayrollRun run, int count) {
        return new CalculationSummaryResponse(run.getId(), run.getStatus().name(),
                run.getCalculationVersion(), run.getCalculatedAt(), count);
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private String basisJson(Map<String, ?> data) {
        return toJson(data);
    }

    private String explanationJson(PayrollRun run, BigDecimal eligibleDays, BigDecimal lopDays,
                                   BigDecimal payableDays, int calendarDaysInMonth,
                                   BigDecimal basic, BigDecimal hra, BigDecimal other,
                                   BigDecimal gross) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", "PRE_STATUTORY");
        data.put("prorationBasis", "CALENDAR_DAY");
        data.put("calendarDaysInMonth", calendarDaysInMonth);
        data.put("eligibleCalendarDays", roundDays(eligibleDays).toPlainString());
        data.put("lopDays", roundDays(lopDays).toPlainString());
        data.put("payableCalendarDays", roundDays(payableDays).toPlainString());
        data.put("basic", basic.toPlainString());
        data.put("hra", hra.toPlainString());
        data.put("otherFixedAllowances", other.toPlainString());
        data.put("grossEarnings", round(gross).toPlainString());
        data.put("statutory", "NOT_CALCULATED");
        data.put("netPay", "NOT_CALCULATED");
        return toJson(data);
    }

    private String toJson(Object data) {
        // Jackson 3 (tools.jackson) throws an unchecked JacksonException on
        // failure; serializing a small map of primitives cannot realistically
        // fail, and any failure must abort the calculation rather than persist an
        // unparseable basis (so it propagates and rolls the transaction back).
        return objectMapper.writeValueAsString(data);
    }

    /**
     * Resolve the caller's active legal entity, enforcing company isolation
     * (cross-company → 404, no disclosure). Mirrors {@link PayrollRunService}.
     */
    private LegalEntity resolveScopedLegalEntity(AuthenticatedUser actor) {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        if (!actor.isPlatform() && (actor.companyId() == null
                || !actor.companyId().equals(legalEntity.getCompanyId()))) {
            throw ApiException.notFound(ApiMessages.PAYROLL_RUN_NOT_FOUND);
        }
        return legalEntity;
    }
}
