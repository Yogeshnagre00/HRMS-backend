package com.example.HRMS.leave.service;

import com.example.HRMS.attendance.repository.AttendanceExceptionRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import com.example.HRMS.leave.dto.LeaveEntryDtos.CreateLeaveEntryRequest;
import com.example.HRMS.leave.dto.LeaveEntryDtos.LeaveEntryResponse;
import com.example.HRMS.leave.entity.EmployeeLeaveBalance;
import com.example.HRMS.leave.entity.LeaveEntry;
import com.example.HRMS.leave.entity.LeaveEntryStatus;
import com.example.HRMS.leave.entity.LeaveEntryTreatment;
import com.example.HRMS.leave.repository.EmployeeLeaveBalanceRepository;
import com.example.HRMS.leave.repository.LeaveEntryRepository;
import com.example.HRMS.tax.service.FinancialYearResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave Entry use cases (API spec 13; task V2-008A.5): create, read, list and
 * cancel admin-entered leave entries, and own paid-leave balance consumption.
 *
 * <p>The employee is resolved through {@link EmployeeService} scope resolution
 * (company isolation; cross-company access yields 404, no disclosure). An entry
 * is valid only within the employee's employment period; at most one RECORDED
 * entry may exist per employee per date; a same-date attendance exception is a
 * blocking conflict. A {@code PAID_LEAVE} entry consumes the employee's
 * current-FY {@link EmployeeLeaveBalance} ({@code used_quantity++},
 * re-derive {@code available_balance}) under a pessimistic row lock, rejecting
 * insufficient balance with 409; {@code UNPAID_LOP_LEAVE} never touches the
 * balance. Cancellation reverses paid-leave consumption exactly once. Payroll
 * calculation never mutates the balance (V2-008A.2; Data Model 19.1.3). Material
 * writes are audited within the transaction.
 *
 * <p><strong>Update scope.</strong> The only authoritative v0 status transition
 * is cancellation (RECORDED → CANCELLED); the entry's identity fields are not
 * mutable via the API (the authoritative documents define no field-edit
 * transition). PUT applies the cancellation transition; DELETE is cancellation.
 */
@Service
public class LeaveEntryService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final LeaveEntryRepository leaveEntryRepository;
    private final EmployeeLeaveBalanceRepository balanceRepository;
    private final AttendanceExceptionRepository attendanceRepository;
    private final EmployeeService employeeService;
    private final LegalEntityService legalEntityService;
    private final FinancialYearResolver financialYearResolver;
    private final AuditService auditService;

    public LeaveEntryService(LeaveEntryRepository leaveEntryRepository,
                             EmployeeLeaveBalanceRepository balanceRepository,
                             AttendanceExceptionRepository attendanceRepository,
                             EmployeeService employeeService,
                             LegalEntityService legalEntityService,
                             FinancialYearResolver financialYearResolver,
                             AuditService auditService) {
        this.leaveEntryRepository = leaveEntryRepository;
        this.balanceRepository = balanceRepository;
        this.attendanceRepository = attendanceRepository;
        this.employeeService = employeeService;
        this.legalEntityService = legalEntityService;
        this.financialYearResolver = financialYearResolver;
        this.auditService = auditService;
    }

    @Transactional
    public LeaveEntryResponse create(AuthenticatedUser actor, CreateLeaveEntryRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, request.employeeId());
        LeaveEntryTreatment treatment = LeaveEntryTreatment.valueOf(request.treatment());
        validateEmploymentPeriod(employee, request.leaveDate());
        validateQuantity(request.quantity());

        // At most one RECORDED entry per employee per date (a cancelled entry does
        // not block a new one).
        if (leaveEntryRepository.findByEmployeeIdAndLeaveDateAndStatus(
                employee.getId(), request.leaveDate(), LeaveEntryStatus.RECORDED).isPresent()) {
            throw ApiException.conflict(ApiMessages.LEAVE_ENTRY_DUPLICATE);
        }
        // Leave + attendance exception on the same date is a blocking conflict.
        if (attendanceRepository.findByEmployeeIdAndAttendanceDate(
                employee.getId(), request.leaveDate()).isPresent()) {
            throw ApiException.conflict(ApiMessages.LEAVE_ENTRY_ATTENDANCE_CONFLICT);
        }

        // Paid leave consumes the current-FY balance under a row lock (serializes
        // concurrent create/cancel; prevents negative balance / lost updates).
        if (treatment == LeaveEntryTreatment.PAID_LEAVE) {
            EmployeeLeaveBalance balance = lockCurrentBalanceOrInsufficient(employee.getId());
            if (balance.getAvailableBalance().compareTo(request.quantity()) < 0) {
                throw ApiException.conflict(ApiMessages.LEAVE_ENTRY_INSUFFICIENT_BALANCE);
            }
            applyConsumption(balance, request.quantity());
        }

        LeaveEntry entry = new LeaveEntry();
        entry.setId(UUID.randomUUID());
        entry.setEmployeeId(employee.getId());
        entry.setLeaveDate(request.leaveDate());
        entry.setTreatment(treatment);
        entry.setQuantity(request.quantity());
        entry.setReason(request.reason());
        entry.setStatus(LeaveEntryStatus.RECORDED);
        entry.setCreatedBy(actor.userId());
        entry.setCreatedAt(LocalDateTime.now());
        leaveEntryRepository.save(entry);

        audit(actor, AuditActions.LEAVE_ENTRY_CREATED, entry.getId());
        return toResponse(entry);
    }

    /** Cancel a leave entry (RECORDED → CANCELLED), reversing paid-leave use once. */
    @Transactional
    public LeaveEntryResponse cancel(AuthenticatedUser actor, UUID id) {
        LeaveEntry entry = resolveScoped(actor, id);
        if (entry.getStatus() == LeaveEntryStatus.CANCELLED) {
            throw ApiException.conflict(ApiMessages.LEAVE_ENTRY_ALREADY_CANCELLED);
        }
        if (entry.getTreatment() == LeaveEntryTreatment.PAID_LEAVE) {
            // Reverse consumption under the same row lock. The balance must exist
            // (it was consumed on create).
            EmployeeLeaveBalance balance = lockCurrentBalanceOrInsufficient(entry.getEmployeeId());
            reverseConsumption(balance, entry.getQuantity());
        }
        entry.setStatus(LeaveEntryStatus.CANCELLED);
        leaveEntryRepository.save(entry);
        audit(actor, AuditActions.LEAVE_ENTRY_CANCELLED, entry.getId());
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public LeaveEntryResponse get(AuthenticatedUser actor, UUID id) {
        return toResponse(resolveScoped(actor, id));
    }

    @Transactional(readOnly = true)
    public Page<LeaveEntryResponse> listForEmployee(AuthenticatedUser actor, UUID employeeId,
                                                    Pageable pageable) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        return leaveEntryRepository.findByEmployeeId(employee.getId(), pageable)
                .map(LeaveEntryService::toResponse);
    }

    // ---- balance helpers --------------------------------------------------

    /**
     * Lock the employee's current-FY balance row. If no balance exists, paid
     * leave cannot be consumed (available treated as zero) — surfaced as
     * insufficient balance rather than silently creating a balance.
     */
    private EmployeeLeaveBalance lockCurrentBalanceOrInsufficient(UUID employeeId) {
        String currentFy = resolveCurrentFinancialYear();
        return balanceRepository
                .findByEmployeeIdAndFinancialYearForUpdate(employeeId, currentFy)
                .orElseThrow(() ->
                        ApiException.conflict(ApiMessages.LEAVE_ENTRY_INSUFFICIENT_BALANCE));
    }

    private void applyConsumption(EmployeeLeaveBalance balance, BigDecimal quantity) {
        balance.setUsedQuantity(balance.getUsedQuantity().add(quantity));
        rederiveAvailable(balance);
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    private void reverseConsumption(EmployeeLeaveBalance balance, BigDecimal quantity) {
        balance.setUsedQuantity(balance.getUsedQuantity().subtract(quantity));
        rederiveAvailable(balance);
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    private static void rederiveAvailable(EmployeeLeaveBalance b) {
        b.setAvailableBalance(b.getOpeningBalance()
                .add(b.getApprovedAdditions())
                .subtract(b.getUsedQuantity()));
    }

    private String resolveCurrentFinancialYear() {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        return financialYearResolver.currentFinancialYear(legalEntity.getFinancialYearStart());
    }

    // ---- validation / scope -----------------------------------------------

    private LeaveEntry resolveScoped(AuthenticatedUser actor, UUID id) {
        LeaveEntry entry = leaveEntryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.LEAVE_ENTRY_NOT_FOUND));
        // resolveScopedEmployee throws 404 (no disclosure) if the owning employee
        // is not in the caller's company scope.
        employeeService.resolveScopedEmployee(actor, entry.getEmployeeId());
        return entry;
    }

    private void validateEmploymentPeriod(Employee employee, LocalDate date) {
        if (date.isBefore(employee.getJoiningDate())
                || (employee.getExitDate() != null && date.isAfter(employee.getExitDate()))) {
            throw ApiException.badRequest(ApiMessages.LEAVE_DATE_OUTSIDE_EMPLOYMENT);
        }
    }

    /** v0 restricts leave quantity to 0.5 or 1.0. */
    private void validateQuantity(BigDecimal quantity) {
        if (quantity.compareTo(HALF) != 0 && quantity.compareTo(ONE) != 0) {
            throw ApiException.badRequest(ApiMessages.LEAVE_ENTRY_QUANTITY_INVALID);
        }
    }

    private void audit(AuthenticatedUser actor, String action, UUID entityId) {
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), action, AuditActions.ENTITY_LEAVE_ENTRY, entityId,
                "SUCCESS", null, null));
    }

    private static LeaveEntryResponse toResponse(LeaveEntry e) {
        return new LeaveEntryResponse(e.getId(), e.getEmployeeId(), e.getLeaveDate(),
                e.getTreatment().name(), e.getQuantity(), e.getReason(), e.getStatus().name(),
                e.getCreatedBy(), e.getCreatedAt());
    }
}
