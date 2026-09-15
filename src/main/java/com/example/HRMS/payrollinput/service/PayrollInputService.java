package com.example.HRMS.payrollinput.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import com.example.HRMS.payroll.entity.PayrollRun;
import com.example.HRMS.payroll.entity.PayrollRunStatus;
import com.example.HRMS.payroll.repository.PayrollRunRepository;
import com.example.HRMS.payrollinput.dto.PayrollInputDtos.ArrearResponse;
import com.example.HRMS.payrollinput.dto.PayrollInputDtos.CreateArrearRequest;
import com.example.HRMS.payrollinput.dto.PayrollInputDtos.CreateVariableEarningRequest;
import com.example.HRMS.payrollinput.dto.PayrollInputDtos.VariableEarningResponse;
import com.example.HRMS.payrollinput.entity.Arrear;
import com.example.HRMS.payrollinput.entity.VariableEarning;
import com.example.HRMS.payrollinput.repository.ArrearRepository;
import com.example.HRMS.payrollinput.repository.VariableEarningRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variable Earning and Arrear use cases (API spec 14.7; task V2-008A.6): create
 * and list independent payroll-period inputs.
 *
 * <p>The employee is resolved through {@link EmployeeService} scope resolution
 * (company isolation; cross-company access yields 404, no disclosure). The
 * referenced {@code PayrollRun} must exist in the employee's legal entity
 * (cross-entity → 409) and be in {@code DRAFT} (V2-008A.6.1; other statuses →
 * 409). Amounts are non-negative (else 400). Creation never prorates, never
 * modifies {@code CompensationRecord}, and never changes the run's calculation
 * state or triggers calculate/recalculate. Multiple inputs per employee + run
 * are allowed. Material creates are audited within the transaction.
 */
@Service
public class PayrollInputService {

    private final VariableEarningRepository variableEarningRepository;
    private final ArrearRepository arrearRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeService employeeService;
    private final AuditService auditService;

    public PayrollInputService(VariableEarningRepository variableEarningRepository,
                               ArrearRepository arrearRepository,
                               PayrollRunRepository payrollRunRepository,
                               EmployeeService employeeService,
                               AuditService auditService) {
        this.variableEarningRepository = variableEarningRepository;
        this.arrearRepository = arrearRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.employeeService = employeeService;
        this.auditService = auditService;
    }

    // ---- Variable Earning -------------------------------------------------

    @Transactional
    public VariableEarningResponse createVariableEarning(AuthenticatedUser actor, UUID employeeId,
                                                         CreateVariableEarningRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        requireDraftRunInSameLegalEntity(employee, request.payrollRunId());
        requireNonNegative(request.amount());

        VariableEarning earning = new VariableEarning();
        earning.setId(UUID.randomUUID());
        earning.setEmployeeId(employee.getId());
        earning.setPayrollRunId(request.payrollRunId());
        earning.setDescription(request.description());
        earning.setAmount(request.amount());
        earning.setSource(request.source());
        earning.setCreatedBy(actor.userId());
        earning.setCreatedAt(LocalDateTime.now());
        variableEarningRepository.save(earning);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.VARIABLE_EARNING_CREATED,
                AuditActions.ENTITY_VARIABLE_EARNING, earning.getId(), "SUCCESS", null, null));
        return toResponse(earning);
    }

    @Transactional(readOnly = true)
    public Page<VariableEarningResponse> listVariableEarnings(AuthenticatedUser actor,
                                                             UUID employeeId, Pageable pageable) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        return variableEarningRepository.findByEmployeeId(employee.getId(), pageable)
                .map(PayrollInputService::toResponse);
    }

    // ---- Arrear -----------------------------------------------------------

    @Transactional
    public ArrearResponse createArrear(AuthenticatedUser actor, UUID employeeId,
                                       CreateArrearRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        requireDraftRunInSameLegalEntity(employee, request.payrollRunId());
        requireNonNegative(request.amount());

        Arrear arrear = new Arrear();
        arrear.setId(UUID.randomUUID());
        arrear.setEmployeeId(employee.getId());
        arrear.setPayrollRunId(request.payrollRunId());
        arrear.setAmount(request.amount());
        arrear.setPeriodReference(request.periodReference());
        arrear.setReason(request.reason());
        arrear.setCreatedBy(actor.userId());
        arrear.setCreatedAt(LocalDateTime.now());
        arrearRepository.save(arrear);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.ARREAR_CREATED, AuditActions.ENTITY_ARREAR,
                arrear.getId(), "SUCCESS", null, null));
        return toResponse(arrear);
    }

    @Transactional(readOnly = true)
    public Page<ArrearResponse> listArrears(AuthenticatedUser actor, UUID employeeId,
                                            Pageable pageable) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        return arrearRepository.findByEmployeeId(employee.getId(), pageable)
                .map(PayrollInputService::toResponse);
    }

    // ---- shared validation ------------------------------------------------

    /**
     * Resolve the referenced payroll run within the employee's legal entity and
     * require it to be in DRAFT. A run outside the employee's legal entity (or
     * not found in scope) is a conflict/not-found; a non-DRAFT run rejects
     * input creation (V2-008A.6.1). No side effects on rejection.
     */
    private void requireDraftRunInSameLegalEntity(Employee employee, UUID payrollRunId) {
        PayrollRun run = payrollRunRepository
                .findByIdAndLegalEntityId(payrollRunId, employee.getLegalEntityId())
                .orElseThrow(() ->
                        ApiException.conflict(ApiMessages.PAYROLL_INPUT_RUN_WRONG_LEGAL_ENTITY));
        if (run.getStatus() != PayrollRunStatus.DRAFT) {
            throw ApiException.conflict(ApiMessages.PAYROLL_INPUT_RUN_NOT_DRAFT);
        }
    }

    private void requireNonNegative(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw ApiException.badRequest(ApiMessages.PAYROLL_INPUT_AMOUNT_NEGATIVE);
        }
    }

    private static VariableEarningResponse toResponse(VariableEarning e) {
        return new VariableEarningResponse(e.getId(), e.getEmployeeId(), e.getPayrollRunId(),
                e.getDescription(), e.getAmount(), e.getSource(), e.getCreatedBy(),
                e.getCreatedAt());
    }

    private static ArrearResponse toResponse(Arrear a) {
        return new ArrearResponse(a.getId(), a.getEmployeeId(), a.getPayrollRunId(), a.getAmount(),
                a.getPeriodReference(), a.getReason(), a.getCreatedBy(), a.getCreatedAt());
    }
}
