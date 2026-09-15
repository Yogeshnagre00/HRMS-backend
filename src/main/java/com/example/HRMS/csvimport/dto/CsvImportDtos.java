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
}
