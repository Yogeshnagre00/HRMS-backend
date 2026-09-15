package com.example.HRMS.leave.service;

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
import com.example.HRMS.leave.dto.LeaveBalanceDtos.LeaveBalanceResponse;
import com.example.HRMS.leave.dto.LeaveBalanceDtos.SetLeaveBalanceRequest;
import com.example.HRMS.leave.entity.EmployeeLeaveBalance;
import com.example.HRMS.leave.entity.LeaveTreatment;
import com.example.HRMS.leave.repository.EmployeeLeaveBalanceRepository;
import com.example.HRMS.tax.service.FinancialYearResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee opening/current paid-leave balance use cases: read and set (create or
 * replace) the current-FY balance (API spec 13).
 *
 * <p>The employee is resolved through {@link EmployeeService} scope resolution
 * (company isolation; cross-company access yields 404, no disclosure). The
 * financial year is server-derived (Data Model 17.1) by reusing the canonical
 * {@link FinancialYearResolver} from V2-003 — there is one FY derivation
 * mechanism. {@code leaveTreatment} is server-set to {@code PAID_LEAVE}.
 * {@code availableBalance} is derived ({@code opening + additions - used}); an
 * insufficient/negative result is stored as-is and surfaced downstream (Business
 * Rules 24.5), never capped or converted to LOP here. Material set actions are
 * audited (the audit references the record id/actor scope).
 */
@Service
public class LeaveBalanceService {

    private final EmployeeLeaveBalanceRepository repository;
    private final EmployeeService employeeService;
    private final LegalEntityService legalEntityService;
    private final FinancialYearResolver financialYearResolver;
    private final AuditService auditService;

    public LeaveBalanceService(EmployeeLeaveBalanceRepository repository,
                               EmployeeService employeeService,
                               LegalEntityService legalEntityService,
                               FinancialYearResolver financialYearResolver,
                               AuditService auditService) {
        this.repository = repository;
        this.employeeService = employeeService;
        this.legalEntityService = legalEntityService;
        this.financialYearResolver = financialYearResolver;
        this.auditService = auditService;
    }

    /** Read the employee's current-FY paid-leave balance. 404 if none set. */
    @Transactional(readOnly = true)
    public LeaveBalanceResponse getLeaveBalance(AuthenticatedUser actor, UUID employeeId) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        String currentFy = resolveCurrentFinancialYear();
        EmployeeLeaveBalance balance = repository
                .findByEmployeeIdAndFinancialYear(employee.getId(), currentFy)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.LEAVE_BALANCE_NOT_FOUND));
        return toResponse(balance);
    }

    /**
     * Set (create or replace) the employee's current-FY paid-leave balance. PUT
     * is a full representation: all three input quantities are supplied and
     * {@code availableBalance} is (re)derived. Employee + FY uniqueness is
     * authoritative at the DB level.
     */
    @Transactional
    public LeaveBalanceResponse setLeaveBalance(AuthenticatedUser actor, UUID employeeId,
                                                SetLeaveBalanceRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        String currentFy = resolveCurrentFinancialYear();

        boolean creating = repository
                .findByEmployeeIdAndFinancialYear(employee.getId(), currentFy).isEmpty();
        EmployeeLeaveBalance balance = repository
                .findByEmployeeIdAndFinancialYear(employee.getId(), currentFy)
                .orElseGet(EmployeeLeaveBalance::new);
        if (creating) {
            balance.setId(UUID.randomUUID());
            balance.setEmployeeId(employee.getId());
            balance.setFinancialYear(currentFy);
        }
        balance.setLeaveTreatment(LeaveTreatment.PAID_LEAVE);
        balance.setOpeningBalance(request.openingBalance());
        balance.setApprovedAdditions(request.approvedAdditions());
        balance.setUsedQuantity(request.usedQuantity());
        // Derived: opening + additions - used. Insufficient/negative is a surfaced
        // state (Business Rules 24.5), never capped to zero or converted to LOP.
        balance.setAvailableBalance(request.openingBalance()
                .add(request.approvedAdditions())
                .subtract(request.usedQuantity()));
        balance.setUpdatedAt(LocalDateTime.now());
        try {
            repository.saveAndFlush(balance);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create for the same employee + FY: unique constraint is
            // authoritative; surface a controlled conflict, never a duplicate.
            throw ApiException.conflict(ApiMessages.LEAVE_BALANCE_CONFLICT);
        }
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                creating ? AuditActions.LEAVE_BALANCE_CREATED : AuditActions.LEAVE_BALANCE_UPDATED,
                AuditActions.ENTITY_EMPLOYEE_LEAVE_BALANCE, balance.getId(),
                "SUCCESS", null, null));
        return toResponse(balance);
    }

    /**
     * Create the current-FY paid-leave balance for an already-resolved employee
     * as part of an atomic CSV import confirmation (V2-006). Reuses the same
     * derivation as the manual set path: {@code leaveTreatment=PAID_LEAVE},
     * {@code approvedAdditions=0}, {@code usedQuantity=0},
     * {@code availableBalance=openingBalance}, FY server-derived. The audit
     * participates in the caller's transaction so it rolls back with a failed
     * confirmation.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public EmployeeLeaveBalance createFromImport(AuthenticatedUser actor, UUID employeeId,
                                                 BigDecimal openingBalance) {
        EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
        balance.setId(UUID.randomUUID());
        balance.setEmployeeId(employeeId);
        balance.setFinancialYear(resolveCurrentFinancialYear());
        balance.setLeaveTreatment(LeaveTreatment.PAID_LEAVE);
        balance.setOpeningBalance(openingBalance);
        balance.setApprovedAdditions(BigDecimal.ZERO);
        balance.setUsedQuantity(BigDecimal.ZERO);
        balance.setAvailableBalance(openingBalance);
        balance.setUpdatedAt(LocalDateTime.now());
        repository.save(balance);
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.LEAVE_BALANCE_CREATED,
                AuditActions.ENTITY_EMPLOYEE_LEAVE_BALANCE, balance.getId(),
                "SUCCESS", null, null));
        return balance;
    }

    private String resolveCurrentFinancialYear() {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        return financialYearResolver.currentFinancialYear(legalEntity.getFinancialYearStart());
    }

    private static LeaveBalanceResponse toResponse(EmployeeLeaveBalance b) {
        return new LeaveBalanceResponse(b.getId(), b.getEmployeeId(),
                b.getLeaveTreatment().name(), b.getFinancialYear(), b.getOpeningBalance(),
                b.getApprovedAdditions(), b.getUsedQuantity(), b.getAvailableBalance(),
                b.getUpdatedAt());
    }
}
