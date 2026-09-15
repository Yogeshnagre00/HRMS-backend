package com.example.HRMS.payroll.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.employee.repository.EmployeeRepository;
import com.example.HRMS.payroll.dto.PayrollRunDtos.CreatePayrollRunRequest;
import com.example.HRMS.payroll.dto.PayrollRunDtos.PayrollRunResponse;
import com.example.HRMS.payroll.entity.PayrollRun;
import com.example.HRMS.payroll.entity.PayrollRunStatus;
import com.example.HRMS.payroll.repository.PayrollRunRepository;
import com.example.HRMS.statutory.entity.RuleVersionSetStatus;
import com.example.HRMS.statutory.entity.StatutoryRuleVersionSet;
import com.example.HRMS.statutory.repository.StatutoryRuleVersionSetRepository;
import com.example.HRMS.tax.service.FinancialYearResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payroll Run Foundation use cases (API spec 15; task V2-007): create a monthly
 * payroll run, list runs (paginated) and read a run.
 *
 * <p>This slice establishes the run only. It does NOT calculate payroll (no
 * proration, LOP, PF/PT/TDS, gross/net), and does not run Health Check, approve,
 * lock, correct or produce outputs. A created run is always {@code DRAFT}.
 *
 * <p>Ownership/scope: the run is owned by the caller's single active v0 legal
 * entity, resolved server-side ({@code legal_entity_id} is never accepted from
 * the client). The financial year is server-derived from the payroll month via
 * the shared {@link FinancialYearResolver} (canonical {@code YYYY-YY},
 * {@code Asia/Kolkata}). The payroll month must be the first day of a calendar
 * month. A primary run is unique by (legal entity, payroll month) — a duplicate
 * is a 409 conflict; correction lineage is a later slice. The referenced
 * statutory rule-version set must exist and be {@code VERIFIED}. Creation +
 * audit are one atomic transaction.
 */
@Service
public class PayrollRunService {

    /**
     * Initial calculation version for a freshly created DRAFT run. The Data Model
     * requires the field to be present at creation but defines no value before a
     * calculation has run; the calculation slice assigns real version identifiers
     * when it produces results. "0" denotes "no calculation yet".
     */
    private static final String INITIAL_CALCULATION_VERSION = "0";

    private final PayrollRunRepository payrollRunRepository;
    private final LegalEntityService legalEntityService;
    private final StatutoryRuleVersionSetRepository ruleVersionSetRepository;
    private final EmployeeRepository employeeRepository;
    private final FinancialYearResolver financialYearResolver;
    private final AuditService auditService;

    public PayrollRunService(PayrollRunRepository payrollRunRepository,
                             LegalEntityService legalEntityService,
                             StatutoryRuleVersionSetRepository ruleVersionSetRepository,
                             EmployeeRepository employeeRepository,
                             FinancialYearResolver financialYearResolver,
                             AuditService auditService) {
        this.payrollRunRepository = payrollRunRepository;
        this.legalEntityService = legalEntityService;
        this.ruleVersionSetRepository = ruleVersionSetRepository;
        this.employeeRepository = employeeRepository;
        this.financialYearResolver = financialYearResolver;
        this.auditService = auditService;
    }

    /**
     * Create a monthly payroll run in {@code DRAFT} status. Validates the payroll
     * month (first-of-month), resolves the server-side legal entity and financial
     * year, validates the referenced rule-version set, and rejects a duplicate
     * primary run for the same (legal entity, payroll month).
     */
    @Transactional
    public PayrollRunResponse createRun(AuthenticatedUser actor, CreatePayrollRunRequest request) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);

        LocalDate payrollMonth = request.payrollMonth();
        if (payrollMonth.getDayOfMonth() != 1) {
            throw ApiException.badRequest(ApiMessages.PAYROLL_MONTH_NOT_FIRST_OF_MONTH);
        }

        // Referenced statutory rule-version set must exist and be VERIFIED. No
        // placeholder/fake row is created here; an unusable set is a conflict.
        StatutoryRuleVersionSet ruleSet = ruleVersionSetRepository
                .findById(request.ruleVersionSetId())
                .orElseThrow(() ->
                        ApiException.notFound(ApiMessages.PAYROLL_RULE_VERSION_SET_NOT_FOUND));
        if (ruleSet.getStatus() != RuleVersionSetStatus.VERIFIED) {
            throw ApiException.conflict(ApiMessages.PAYROLL_RULE_VERSION_SET_NOT_VERIFIED);
        }

        // One primary run per (legal entity, payroll month). Correction lineage
        // (parent_payroll_run_id) is a later slice; this Foundation only creates
        // primary runs, so a duplicate primary is a conflict.
        if (payrollRunRepository.existsByLegalEntityIdAndPayrollMonthAndParentPayrollRunIdIsNull(
                legalEntity.getId(), payrollMonth)) {
            throw ApiException.conflict(ApiMessages.PAYROLL_RUN_ALREADY_EXISTS);
        }

        String financialYear = financialYearResolver.financialYearFor(
                payrollMonth, legalEntity.getFinancialYearStart());

        PayrollRun run = new PayrollRun();
        run.setId(UUID.randomUUID());
        run.setLegalEntityId(legalEntity.getId());
        run.setPayrollMonth(payrollMonth);
        run.setFinancialYear(financialYear);
        run.setStatus(PayrollRunStatus.DRAFT);
        run.setCalculationVersion(INITIAL_CALCULATION_VERSION);
        run.setRuleVersionSetId(ruleSet.getId());
        run.setParentPayrollRunId(null);
        run.setCreatedBy(actor.userId());
        run.setCreatedAt(LocalDateTime.now());
        try {
            payrollRunRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create of the same primary run: surface a controlled
            // conflict rather than a duplicate/opaque error.
            throw ApiException.conflict(ApiMessages.PAYROLL_RUN_ALREADY_EXISTS);
        }

        // Audit participates in this transaction so a failed creation leaves no
        // "created" audit (recordInTransaction, not REQUIRES_NEW).
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.PAYROLL_RUN_CREATED, AuditActions.ENTITY_PAYROLL_RUN,
                run.getId(), "SUCCESS", null, null));

        return toResponse(run, eligibleEmployeeCount(legalEntity, payrollMonth));
    }

    /** List payroll runs for the caller's legal entity (paginated, deterministic). */
    @Transactional(readOnly = true)
    public Page<PayrollRunResponse> listRuns(AuthenticatedUser actor, Pageable pageable) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        return payrollRunRepository.findByLegalEntityId(legalEntity.getId(), pageable)
                .map(run -> toResponse(run,
                        eligibleEmployeeCount(legalEntity, run.getPayrollMonth())));
    }

    /** Read one payroll run, enforcing company/legal-entity isolation (404, no disclosure). */
    @Transactional(readOnly = true)
    public PayrollRunResponse getRun(AuthenticatedUser actor, UUID runId) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        PayrollRun run = payrollRunRepository.findByIdAndLegalEntityId(runId, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.PAYROLL_RUN_NOT_FOUND));
        return toResponse(run, eligibleEmployeeCount(legalEntity, run.getPayrollMonth()));
    }

    /**
     * Automatic employee population size: employees whose employment period
     * intersects the payroll month (joining/exit inclusive). Day-level payable
     * calculation belongs to the calculation slice, not here.
     */
    private long eligibleEmployeeCount(LegalEntity legalEntity, LocalDate payrollMonth) {
        LocalDate monthStart = payrollMonth.withDayOfMonth(1);
        LocalDate monthEnd = payrollMonth.withDayOfMonth(payrollMonth.lengthOfMonth());
        return employeeRepository.countEligibleForPayrollMonth(
                legalEntity.getId(), monthStart, monthEnd);
    }

    /**
     * Resolve the caller's active legal entity, enforcing company isolation. v0
     * has one active legal entity; a company-scoped caller must belong to that
     * entity's company, otherwise the resource is treated as not found (no
     * cross-company disclosure). Platform actors are unrestricted.
     */
    private LegalEntity resolveScopedLegalEntity(AuthenticatedUser actor) {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        if (!actor.isPlatform() && (actor.companyId() == null
                || !actor.companyId().equals(legalEntity.getCompanyId()))) {
            throw ApiException.notFound(ApiMessages.PAYROLL_RUN_NOT_FOUND);
        }
        return legalEntity;
    }

    private static PayrollRunResponse toResponse(PayrollRun r, long eligibleEmployeeCount) {
        return new PayrollRunResponse(r.getId(), r.getLegalEntityId(), r.getPayrollMonth(),
                r.getFinancialYear(), r.getStatus().name(), r.getCalculationVersion(),
                r.getRuleVersionSetId(), r.getParentPayrollRunId(), eligibleEmployeeCount,
                r.getCalculatedAt(), r.getApprovedAt(), r.getApprovedBy(), r.getLockedAt(),
                r.getLockedBy(), r.getCreatedBy(), r.getCreatedAt());
    }
}
