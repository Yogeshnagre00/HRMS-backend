package com.example.HRMS.csvimport.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.csvimport.dto.CsvImportDtos.ValidationIssue;
import com.example.HRMS.csvimport.dto.CsvImportDtos.ValidationResultResponse;
import com.example.HRMS.csvimport.dto.ValidationSeverity;
import com.example.HRMS.csvimport.entity.ImportSession;
import com.example.HRMS.csvimport.entity.ImportSessionRow;
import com.example.HRMS.csvimport.entity.ImportSessionStatus;
import com.example.HRMS.csvimport.repository.ImportSessionRepository;
import com.example.HRMS.csvimport.repository.ImportSessionRowRepository;
import com.example.HRMS.csvimport.validation.CsvHeaders;
import com.example.HRMS.csvimport.validation.CsvIssueCodes;
import com.example.HRMS.csvimport.validation.CsvParser;
import com.example.HRMS.csvimport.validation.RowValidator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Employee CSV validation (API §11). Parses an uploaded CSV, validates the
 * header against the canonical contract and each data row against the existing
 * domain rules, persists an {@link ImportSession} + {@link ImportSessionRow}s in
 * {@code VALIDATED} status, and returns the validation result.
 *
 * <p>This service performs <strong>no business-data mutation</strong>: it never
 * creates or modifies Employee, bank, opening-tax-state or leave-balance
 * records. It is company/legal-entity scoped (resolved server-side). Confirmation
 * (V2-006) is not implemented here.
 */
@Service
public class CsvValidationService {

    private static final String STATUS_PASSED = "VALIDATION_PASSED";
    private static final String STATUS_FAILED = "VALIDATION_FAILED";

    private final ImportSessionRepository sessionRepository;
    private final ImportSessionRowRepository rowRepository;
    private final LegalEntityService legalEntityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CsvValidationService(ImportSessionRepository sessionRepository,
                                ImportSessionRowRepository rowRepository,
                                LegalEntityService legalEntityService,
                                AuditService auditService,
                                ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.rowRepository = rowRepository;
        this.legalEntityService = legalEntityService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate a CSV file and persist the validation session. Returns the result
     * DTO (never entities). Does not modify any employee business data.
     */
    @Transactional
    public ValidationResultResponse validate(AuthenticatedUser actor, String fileName,
                                             String content) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);

        List<List<String>> records = CsvParser.parse(content == null ? "" : content);
        List<ValidationIssue> issues = new ArrayList<>();

        if (records.isEmpty()) {
            issues.add(new ValidationIssue(0, null, CsvIssueCodes.FILE_EMPTY,
                    ApiMessages.CSV_FILE_EMPTY, ValidationSeverity.BLOCKING));
            return persistAndBuild(actor, legalEntity, fileName, 0, 0, 0, 0, issues, List.of());
        }

        List<String> header = records.get(0);
        List<ValidationIssue> headerIssues = validateHeader(header);
        if (!headerIssues.isEmpty()) {
            // Header is invalid: report header issues; do not attempt row validation.
            issues.addAll(headerIssues);
            return persistAndBuild(actor, legalEntity, fileName, 0, 0, 0, 0, issues, List.of());
        }

        // Header valid → validate each data row (physical row number = index + 1,
        // header is row 1 so first data row is row 2).
        // First pass: identify Employee IDs that appear more than once within the file.
        Map<String, Integer> employeeIdCount = new LinkedHashMap<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> values = records.get(r);
            Map<String, String> mapped = mapRow(header, values);
            String empId = mapped.get(CsvHeaders.EMPLOYEE_ID);
            if (empId != null && !empId.isBlank()) {
                employeeIdCount.merge(empId.trim(), 1, Integer::sum);
            }
        }

        // Second pass: validate each row.
        int total = 0;
        int valid = 0;
        int invalid = 0;
        int warning = 0;
        List<RowState> rowStates = new ArrayList<>();

        for (int r = 1; r < records.size(); r++) {
            int rowNumber = r + 1;
            total++;
            List<String> values = records.get(r);
            Map<String, String> mapped = mapRow(header, values);
            List<ValidationIssue> rowIssues = new ArrayList<>();

            if (values.size() != header.size()) {
                rowIssues.add(new ValidationIssue(rowNumber, null,
                        CsvIssueCodes.ROW_COLUMN_COUNT_MISMATCH,
                        ApiMessages.CSV_ROW_COLUMN_COUNT_MISMATCH, ValidationSeverity.BLOCKING));
            }
            rowIssues.addAll(RowValidator.validate(rowNumber, mapped));

            // Duplicate Employee ID within the file: flag all occurrences if ID
            // appears more than once (all such rows are invalid — no first-wins/last-wins).
            String empId = mapped.get(CsvHeaders.EMPLOYEE_ID);
            if (empId != null && !empId.isBlank()
                    && employeeIdCount.getOrDefault(empId.trim(), 0) > 1) {
                rowIssues.add(new ValidationIssue(rowNumber, CsvHeaders.EMPLOYEE_ID,
                        CsvIssueCodes.DUPLICATE_EMPLOYEE_ID_IN_FILE,
                        ApiMessages.CSV_DUPLICATE_EMPLOYEE_ID_IN_FILE,
                        ValidationSeverity.BLOCKING));
            }

            boolean hasBlocking = rowIssues.stream()
                    .anyMatch(i -> i.severity() == ValidationSeverity.BLOCKING);
            boolean hasWarning = rowIssues.stream()
                    .anyMatch(i -> i.severity() == ValidationSeverity.WARNING);
            if (hasBlocking) {
                invalid++;
            } else {
                valid++;
            }
            if (hasWarning && !hasBlocking) {
                warning++;
            }
            issues.addAll(rowIssues);
            rowStates.add(new RowState(rowNumber, !hasBlocking, mapped, sortIssues(rowIssues)));
        }

        return persistAndBuild(actor, legalEntity, fileName, total, valid, invalid, warning,
                issues, rowStates);
    }

    /** Read a persisted validation session's result (company-scoped). */
    @Transactional(readOnly = true)
    public ValidationResultResponse getSession(AuthenticatedUser actor, UUID importId) {
        UUID companyId = requireCompanyScope(actor);
        ImportSession session = sessionRepository.findByIdAndCompanyId(importId, companyId)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.CSV_IMPORT_SESSION_NOT_FOUND));
        List<ValidationIssue> issues = new ArrayList<>();
        for (ImportSessionRow row : rowRepository
                .findByImportSessionIdOrderByRowNumberAsc(session.getId())) {
            issues.addAll(deserializeIssues(row.getIssues()));
        }
        String status = session.getInvalidRows() > 0 ? STATUS_FAILED : STATUS_PASSED;
        return new ValidationResultResponse(session.getId(), status, session.getTotalRows(),
                session.getValidRows(), session.getInvalidRows(), session.getWarningRows(),
                sortIssues(issues));
    }

    private ValidationResultResponse persistAndBuild(AuthenticatedUser actor,
            LegalEntity legalEntity, String fileName, int total, int valid, int invalid,
            int warning, List<ValidationIssue> allIssues, List<RowState> rowStates) {
        List<ValidationIssue> ordered = sortIssues(allIssues);
        boolean hasBlocking = ordered.stream()
                .anyMatch(i -> i.severity() == ValidationSeverity.BLOCKING);
        String status = hasBlocking ? STATUS_FAILED : STATUS_PASSED;

        LocalDateTime now = LocalDateTime.now();
        ImportSession session = new ImportSession();
        session.setId(UUID.randomUUID());
        session.setCompanyId(legalEntity.getCompanyId());
        session.setLegalEntityId(legalEntity.getId());
        session.setStatus(ImportSessionStatus.VALIDATED);
        session.setOriginalFileName(fileName);
        session.setTotalRows(total);
        session.setValidRows(valid);
        session.setInvalidRows(invalid);
        session.setWarningRows(warning);
        session.setCreatedAt(now);
        session.setValidatedAt(now);
        sessionRepository.save(session);

        for (RowState rs : rowStates) {
            ImportSessionRow row = new ImportSessionRow();
            row.setId(UUID.randomUUID());
            row.setImportSessionId(session.getId());
            row.setRowNumber(rs.rowNumber());
            row.setValid(rs.valid());
            row.setValidatedData(serialize(rs.validatedData()));
            row.setIssues(serialize(rs.issues()));
            rowRepository.save(row);
        }

        // Audit the administrative validation action (no sensitive payload).
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.IMPORT_SESSION_VALIDATED, AuditActions.ENTITY_IMPORT_SESSION,
                session.getId(), "SUCCESS", null, null));

        return new ValidationResultResponse(session.getId(), status, total, valid, invalid,
                warning, ordered);
    }

    private List<ValidationIssue> validateHeader(List<String> header) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String h : header) {
            if (h == null || h.isBlank()) {
                issues.add(new ValidationIssue(1, null, CsvIssueCodes.HEADER_BLANK,
                        ApiMessages.CSV_HEADER_BLANK, ValidationSeverity.BLOCKING));
                continue;
            }
            counts.merge(h, 1, Integer::sum);
            if (!CsvHeaders.CANONICAL.contains(h)) {
                issues.add(new ValidationIssue(1, h, CsvIssueCodes.HEADER_UNKNOWN,
                        ApiMessages.CSV_HEADER_UNKNOWN, ValidationSeverity.BLOCKING));
            }
        }
        // Duplicate canonical headers.
        counts.forEach((h, c) -> {
            if (c > 1 && CsvHeaders.CANONICAL.contains(h)) {
                issues.add(new ValidationIssue(1, h, CsvIssueCodes.HEADER_DUPLICATE,
                        ApiMessages.CSV_HEADER_DUPLICATE, ValidationSeverity.BLOCKING));
            }
        });
        // Missing required (all canonical) headers.
        for (String canonical : CsvHeaders.CANONICAL) {
            if (!header.contains(canonical)) {
                issues.add(new ValidationIssue(1, canonical, CsvIssueCodes.HEADER_MISSING,
                        ApiMessages.CSV_HEADER_MISSING, ValidationSeverity.BLOCKING));
            }
        }
        return issues;
    }

    private static Map<String, String> mapRow(List<String> header, List<String> values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            map.put(header.get(i), i < values.size() ? values.get(i) : null);
        }
        return map;
    }

    /** Deterministic ordering: rowNumber, field canonical position, severity, code. */
    private static List<ValidationIssue> sortIssues(List<ValidationIssue> issues) {
        List<ValidationIssue> copy = new ArrayList<>(issues);
        copy.sort(Comparator
                .comparingInt(ValidationIssue::rowNumber)
                .thenComparingInt((ValidationIssue i) ->
                        i.field() == null ? -1 : CsvHeaders.position(i.field()))
                .thenComparing(i -> i.severity().name())
                .thenComparing(ValidationIssue::code));
        return copy;
    }

    private LegalEntity resolveScopedLegalEntity(AuthenticatedUser actor) {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        if (!actor.isPlatform() && (actor.companyId() == null
                || !actor.companyId().equals(legalEntity.getCompanyId()))) {
            throw ApiException.notFound(ApiMessages.CSV_IMPORT_SESSION_NOT_FOUND);
        }
        return legalEntity;
    }

    private UUID requireCompanyScope(AuthenticatedUser actor) {
        if (actor.isPlatform()) {
            return legalEntityService.resolveActiveLegalEntity().getCompanyId();
        }
        if (actor.companyId() == null) {
            throw ApiException.notFound(ApiMessages.CSV_IMPORT_SESSION_NOT_FOUND);
        }
        return actor.companyId();
    }

    private String serialize(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private List<ValidationIssue> deserializeIssues(String json) {
        return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class,
                        ValidationIssue.class));
    }

    /** Transient per-row state carried into persistence. */
    private record RowState(int rowNumber, boolean valid, Map<String, String> validatedData,
                            List<ValidationIssue> issues) {
    }
}
