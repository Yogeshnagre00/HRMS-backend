package com.example.HRMS.csvimport.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response DTOs for the Employee CSV validation API (API §11.4). Persistence
 * entities are never exposed.
 */
public final class CsvImportDtos {

    private CsvImportDtos() {
    }

    /** A single validation issue in the deterministic issue list. */
    public record ValidationIssue(
            int rowNumber,
            String field,
            String code,
            String message,
            ValidationSeverity severity) {
    }

    /**
     * Result of validating an uploaded CSV, backed by a persisted import session.
     * {@code status} is {@code VALIDATION_PASSED} (zero BLOCKING issues) or
     * {@code VALIDATION_FAILED}. Counts describe data rows (the header row is not
     * counted). Every data row is represented; invalid rows are not dropped.
     */
    public record ValidationResultResponse(
            UUID importId,
            String status,
            int totalRows,
            int validRows,
            int invalidRows,
            int warningRows,
            List<ValidationIssue> issues) {
    }

    /**
     * Confirmation request (API §11.6). The body is optional: an empty body or
     * {@code {"confirm": true}} both confirm. The client never resends the CSV or
     * controls which rows are applied — confirmation operates on the persisted
     * validation session. {@code confirm} is accepted but not required.
     */
    public record ConfirmImportRequest(Boolean confirm) {
    }

    /**
     * Result of confirming an import session (API §11.6). Persistence entities are
     * never exposed and no sensitive values (PAN, account number, salary) are
     * included.
     */
    public record ConfirmImportResponse(
            UUID importId,
            String status,
            int totalRowsApplied,
            java.time.LocalDateTime confirmedAt) {
    }
}
