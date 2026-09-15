package com.example.HRMS.csvimport.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.bank.service.BankAccountService;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.compensation.dto.CompensationDtos.CompensationRequest;
import com.example.HRMS.compensation.service.CompensationService;
import com.example.HRMS.csvimport.dto.CsvImportDtos.ConfirmImportResponse;
import com.example.HRMS.csvimport.entity.ImportSession;
import com.example.HRMS.csvimport.entity.ImportSessionRow;
import com.example.HRMS.csvimport.entity.ImportSessionStatus;
import com.example.HRMS.csvimport.repository.ImportSessionRepository;
import com.example.HRMS.csvimport.repository.ImportSessionRowRepository;
import com.example.HRMS.csvimport.validation.CsvHeaders;
import com.example.HRMS.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import com.example.HRMS.leave.service.LeaveBalanceService;
import com.example.HRMS.tax.service.OpeningTaxStateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Employee CSV import confirmation orchestration (API §11.6, task V2-006).
 *
 * <p>Transitions a {@code VALIDATED} import session to {@code CONFIRMED} and
 * atomically persists the accepted employee business data by delegating to the
 * existing domain services (Employee, Compensation, Bank, Opening Tax State,
 * Leave Balance). It does not re-implement any domain rule and never reparses
 * the raw CSV — it reads the persisted, validated row data.
 *
 * <p><strong>Create-only.</strong> Confirmation only creates new employees. An
 * Employee ID that already exists within the company's legal entity is a
 * blocking conflict (409); the entire confirmation rolls back and no business
 * data is changed (there is no partial import, and existing employees are never
 * updated).
 *
 * <p><strong>Atomicity.</strong> The whole operation is one transaction; if any
 * step fails, everything (including the per-entity creation audit events, which
 * are recorded transactionally) rolls back and the session stays
 * {@code VALIDATED}. The confirmation success audit and status transition commit
 * only if all business data persisted.
 */
@Service
public class CsvImportConfirmationService {

    private final ImportSessionRepository sessionRepository;
    private final ImportSessionRowRepository rowRepository;
    private final EmployeeService employeeService;
    private final CompensationService compensationService;
    private final BankAccountService bankAccountService;
    private final OpeningTaxStateService openingTaxStateService;
    private final LeaveBalanceService leaveBalanceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CsvImportConfirmationService(ImportSessionRepository sessionRepository,
                                        ImportSessionRowRepository rowRepository,
                                        EmployeeService employeeService,
                                        CompensationService compensationService,
                                        BankAccountService bankAccountService,
                                        OpeningTaxStateService openingTaxStateService,
                                        LeaveBalanceService leaveBalanceService,
                                        AuditService auditService,
                                        ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.rowRepository = rowRepository;
        this.employeeService = employeeService;
        this.compensationService = compensationService;
        this.bankAccountService = bankAccountService;
        this.openingTaxStateService = openingTaxStateService;
        this.leaveBalanceService = leaveBalanceService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Confirm a validated import session. One atomic transaction; create-only for
     * employees. Returns the confirmation result DTO (never entities, never
     * sensitive values).
     */
    @Transactional
    public ConfirmImportResponse confirm(AuthenticatedUser actor, UUID importId) {
        // 1-4. Resolve scope + load the session within the caller's company.
        UUID companyId = requireCompanyScope(actor);
        ImportSession session = sessionRepository.findByIdAndCompanyId(importId, companyId)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.CSV_IMPORT_SESSION_NOT_FOUND));

        // 5. Validation gate: only a VALIDATED session with zero invalid rows.
        if (session.getStatus() == ImportSessionStatus.CONFIRMED) {
            throw ApiException.conflict(ApiMessages.CSV_IMPORT_ALREADY_CONFIRMED);
        }
        if (session.getStatus() != ImportSessionStatus.VALIDATED) {
            throw ApiException.conflict(ApiMessages.CSV_IMPORT_NOT_VALIDATED);
        }
        if (session.getInvalidRows() > 0) {
            throw ApiException.conflict(ApiMessages.CSV_IMPORT_HAS_INVALID_ROWS);
        }

        // 6. Load validated rows (persisted state; never reparse the CSV).
        List<ImportSessionRow> rows =
                rowRepository.findByImportSessionIdOrderByRowNumberAsc(session.getId());
        List<Map<String, String>> data = new ArrayList<>(rows.size());
        for (ImportSessionRow row : rows) {
            // Defensive invariant: a confirmable session has no invalid rows.
            if (!row.isValid()) {
                throw ApiException.conflict(ApiMessages.CSV_IMPORT_HAS_INVALID_ROWS);
            }
            data.add(parse(row.getValidatedData()));
        }

        // Resolve the caller's legal entity once for the whole confirmation.
        LegalEntity legalEntity = employeeService.resolveScopedLegalEntityForImport(actor);

        // 7. Create-only conflict detection: fail before persisting any data if
        // any Employee ID already exists in the legal entity.
        for (Map<String, String> r : data) {
            String employeeId = value(r, CsvHeaders.EMPLOYEE_ID);
            if (employeeService.employeeIdExists(legalEntity.getId(), employeeId)) {
                throw ApiException.conflict(ApiMessages.CSV_IMPORT_EMPLOYEE_ID_EXISTS);
            }
        }

        // 8-12. Persist business data per row (create-only). A duplicate Employee
        // ID appearing concurrently is caught by the domain uniqueness rule and
        // surfaces as the same create-only conflict.
        int applied = 0;
        try {
            for (Map<String, String> r : data) {
                Employee employee = employeeService.createEmployeeFromImport(actor, legalEntity,
                        toCreateEmployeeRequest(r));
                compensationService.createFirstFromImport(actor, employee.getId(),
                        toCompensationRequest(r));
                bankAccountService.createFromImport(actor, employee.getId(),
                        value(r, CsvHeaders.ACCOUNT_NUMBER), value(r, CsvHeaders.IFSC));
                openingTaxStateService.createFromImport(actor, employee.getId(),
                        money(r, CsvHeaders.CUMULATIVE_TAXABLE_INCOME),
                        money(r, CsvHeaders.TDS_ALREADY_DEDUCTED));
                leaveBalanceService.createFromImport(actor, employee.getId(),
                        money(r, CsvHeaders.OPENING_LEAVE_BALANCE));
                applied++;
            }
        } catch (DataIntegrityViolationException ex) {
            // Concurrent creation of the same Employee ID: DB uniqueness is
            // authoritative; surface the create-only conflict, roll back everything.
            throw ApiException.conflict(ApiMessages.CSV_IMPORT_EMPLOYEE_ID_EXISTS);
        }

        // 13-15. Transition session and record confirmation metadata + audit
        // (all within this transaction so they roll back on any failure above).
        session.setStatus(ImportSessionStatus.CONFIRMED);
        session.setConfirmedAt(LocalDateTime.now());
        sessionRepository.save(session);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.IMPORT_SESSION_CONFIRMED,
                AuditActions.ENTITY_IMPORT_SESSION, session.getId(), "SUCCESS", null, null));

        // 16. Commit happens on return. totalRowsApplied equals the applied row
        // count (all validated rows; there is no partial import).
        return new ConfirmImportResponse(session.getId(), session.getStatus().name(), applied,
                session.getConfirmedAt());
    }

    private UUID requireCompanyScope(AuthenticatedUser actor) {
        // Reuse the same scope model as the rest of the import flow: a platform
        // actor operates on the single active legal entity's company; a company
        // actor is confined to its own company. Cross-company / missing import is
        // reported as 404 (no disclosure) by the company-scoped session lookup.
        if (actor.isPlatform()) {
            return employeeService.resolveScopedLegalEntityForImport(actor).getCompanyId();
        }
        if (actor.companyId() == null) {
            throw ApiException.notFound(ApiMessages.CSV_IMPORT_SESSION_NOT_FOUND);
        }
        return actor.companyId();
    }

    private CreateEmployeeRequest toCreateEmployeeRequest(Map<String, String> r) {
        return new CreateEmployeeRequest(
                value(r, CsvHeaders.EMPLOYEE_ID),
                value(r, CsvHeaders.NAME),
                date(r, CsvHeaders.JOINING_DATE),
                dateOrNull(r, CsvHeaders.EXIT_DATE),
                value(r, CsvHeaders.EMPLOYMENT_TYPE),
                valueOrNull(r, CsvHeaders.DEPARTMENT),
                valueOrNull(r, CsvHeaders.DESIGNATION),
                valueOrNull(r, CsvHeaders.LOCATION),
                value(r, CsvHeaders.PAN),
                valueOrNull(r, CsvHeaders.UAN),
                valueOrNull(r, CsvHeaders.PT_STATE),
                value(r, CsvHeaders.TAX_REGIME),
                EMPLOYEE_STATUS_ACTIVE);
    }

    private CompensationRequest toCompensationRequest(Map<String, String> r) {
        return new CompensationRequest(
                date(r, CsvHeaders.EFFECTIVE_DATE),
                money(r, CsvHeaders.CTC),
                money(r, CsvHeaders.BASIC),
                money(r, CsvHeaders.HRA),
                money(r, CsvHeaders.OTHER_ALLOWANCES),
                null);
    }

    // New employees created via CSV import are ACTIVE (Screen S07 onboarding).
    private static final String EMPLOYEE_STATUS_ACTIVE = "ACTIVE";

    private Map<String, String> parse(String json) {
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
    }

    /** Required value: trimmed (validated non-blank at V2-005). */
    private static String value(Map<String, String> r, String header) {
        String v = r.get(header);
        return v == null ? null : v.trim();
    }

    /** Optional value: null when blank/absent. */
    private static String valueOrNull(Map<String, String> r, String header) {
        String v = r.get(header);
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    private static LocalDate date(Map<String, String> r, String header) {
        return LocalDate.parse(value(r, header));
    }

    private static LocalDate dateOrNull(Map<String, String> r, String header) {
        String v = valueOrNull(r, header);
        return v == null ? null : LocalDate.parse(v);
    }

    private static BigDecimal money(Map<String, String> r, String header) {
        String v = valueOrNull(r, header);
        return v == null ? null : new BigDecimal(v);
    }
}
