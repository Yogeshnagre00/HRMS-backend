package com.example.HRMS.tax.service;

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
import com.example.HRMS.tax.dto.OpeningTaxStateDtos.OpeningTaxStateResponse;
import com.example.HRMS.tax.dto.OpeningTaxStateDtos.PatchOpeningTaxStateRequest;
import com.example.HRMS.tax.entity.EmployeeOpeningTaxState;
import com.example.HRMS.tax.entity.OpeningTaxStateSource;
import com.example.HRMS.tax.repository.EmployeeOpeningTaxStateRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee opening FY tax state use cases: read and create-or-partially-update
 * the current-financial-year opening state (API spec 10.1).
 *
 * <p>The employee is resolved through {@link EmployeeService} scope resolution
 * (company isolation; cross-company access yields 404, no disclosure). The
 * financial year is server-derived (Data Model 17.1) via
 * {@link FinancialYearResolver} using the active legal entity's
 * {@code financial_year_start}; the client never supplies it. {@code source} is
 * server-assigned {@code MANUAL}. This service stores/retrieves state only — it
 * performs no TDS/tax calculation. Material create/update is audited (the audit
 * references the record id/actor scope, not the monetary payload).
 */
@Service
public class OpeningTaxStateService {

    private final EmployeeOpeningTaxStateRepository repository;
    private final EmployeeService employeeService;
    private final LegalEntityService legalEntityService;
    private final FinancialYearResolver financialYearResolver;
    private final AuditService auditService;

    public OpeningTaxStateService(EmployeeOpeningTaxStateRepository repository,
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

    /** Read the employee's current-FY opening tax state. 404 if none set. */
    @Transactional(readOnly = true)
    public OpeningTaxStateResponse getOpeningTaxState(AuthenticatedUser actor, UUID employeeId) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        String currentFy = resolveCurrentFinancialYear();
        EmployeeOpeningTaxState state = repository
                .findByEmployeeIdAndFinancialYear(employee.getId(), currentFy)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.OPENING_TAX_STATE_NOT_FOUND));
        return toResponse(state);
    }

    /**
     * Create the current-FY opening tax state if none exists, or partially update
     * it if it does. Only supplied fields change on update; on create both
     * monetary fields are required.
     */
    @Transactional
    public OpeningTaxStateResponse patchOpeningTaxState(AuthenticatedUser actor, UUID employeeId,
                                                        PatchOpeningTaxStateRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        String currentFy = resolveCurrentFinancialYear();

        return repository.findByEmployeeIdAndFinancialYear(employee.getId(), currentFy)
                .map(existing -> update(actor, existing, request))
                .orElseGet(() -> create(actor, employee.getId(), currentFy, request));
    }

    /**
     * Create the current-FY opening tax state for an already-resolved employee as
     * part of an atomic CSV import confirmation (V2-006). {@code source} is
     * {@code CSV_IMPORT}; the FY is server-derived (same resolver as the manual
     * path). Both monetary values are required (no silent zero default). The
     * audit participates in the caller's transaction so it rolls back with a
     * failed confirmation.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public EmployeeOpeningTaxState createFromImport(AuthenticatedUser actor, UUID employeeId,
                                                    BigDecimal cumulativeTaxableIncome,
                                                    BigDecimal tdsAlreadyDeducted) {
        if (cumulativeTaxableIncome == null || tdsAlreadyDeducted == null) {
            throw ApiException.badRequest(ApiMessages.OPENING_TAX_STATE_INCOMPLETE);
        }
        EmployeeOpeningTaxState state = new EmployeeOpeningTaxState();
        state.setId(UUID.randomUUID());
        state.setEmployeeId(employeeId);
        state.setFinancialYear(resolveCurrentFinancialYear());
        state.setCumulativeTaxableIncome(cumulativeTaxableIncome);
        state.setTdsAlreadyDeducted(tdsAlreadyDeducted);
        state.setSource(OpeningTaxStateSource.CSV_IMPORT);
        state.setCreatedAt(LocalDateTime.now());
        repository.save(state);
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.OPENING_TAX_STATE_CREATED,
                AuditActions.ENTITY_EMPLOYEE_OPENING_TAX_STATE, state.getId(),
                "SUCCESS", null, null));
        return state;
    }

    private OpeningTaxStateResponse create(AuthenticatedUser actor, UUID employeeId,
                                           String financialYear,
                                           PatchOpeningTaxStateRequest request) {
        // On create both opening values are required (no silent zero default).
        if (request.cumulativeTaxableIncome() == null || request.tdsAlreadyDeducted() == null) {
            throw ApiException.badRequest(ApiMessages.OPENING_TAX_STATE_INCOMPLETE);
        }
        EmployeeOpeningTaxState state = new EmployeeOpeningTaxState();
        state.setId(UUID.randomUUID());
        state.setEmployeeId(employeeId);
        state.setFinancialYear(financialYear);
        state.setCumulativeTaxableIncome(request.cumulativeTaxableIncome());
        state.setTdsAlreadyDeducted(request.tdsAlreadyDeducted());
        state.setSource(OpeningTaxStateSource.MANUAL);
        state.setCreatedAt(LocalDateTime.now());
        try {
            repository.saveAndFlush(state);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create for the same employee + FY: the unique constraint
            // is authoritative; surface a controlled conflict, never a duplicate.
            throw ApiException.conflict(ApiMessages.OPENING_TAX_STATE_CONFLICT);
        }
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.OPENING_TAX_STATE_CREATED,
                AuditActions.ENTITY_EMPLOYEE_OPENING_TAX_STATE, state.getId(),
                "SUCCESS", null, null));
        return toResponse(state);
    }

    private OpeningTaxStateResponse update(AuthenticatedUser actor, EmployeeOpeningTaxState state,
                                           PatchOpeningTaxStateRequest request) {
        // Partial update: only supplied fields change; omitted fields preserved.
        if (request.cumulativeTaxableIncome() != null) {
            state.setCumulativeTaxableIncome(request.cumulativeTaxableIncome());
        }
        if (request.tdsAlreadyDeducted() != null) {
            state.setTdsAlreadyDeducted(request.tdsAlreadyDeducted());
        }
        repository.save(state);
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.OPENING_TAX_STATE_UPDATED,
                AuditActions.ENTITY_EMPLOYEE_OPENING_TAX_STATE, state.getId(),
                "SUCCESS", null, null));
        return toResponse(state);
    }

    private String resolveCurrentFinancialYear() {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        return financialYearResolver.currentFinancialYear(legalEntity.getFinancialYearStart());
    }

    private static OpeningTaxStateResponse toResponse(EmployeeOpeningTaxState s) {
        return new OpeningTaxStateResponse(s.getId(), s.getEmployeeId(), s.getFinancialYear(),
                s.getCumulativeTaxableIncome(), s.getTdsAlreadyDeducted(), s.getSource().name(),
                s.getCreatedAt());
    }
}
