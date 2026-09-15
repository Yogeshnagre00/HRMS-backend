package com.example.HRMS.csvimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Per-row validated state and issues for an {@link ImportSession} (table
 * {@code import_session_row}; Data Model 7.5).
 *
 * <p>{@code validatedData} and {@code issues} hold JSON as text (Data Model
 * "json/text"), sufficient for the validation result and for deterministic
 * V2-006 confirmation without re-interpreting the raw CSV. {@code rowNumber} is
 * the physical CSV row number including the header (header = 1, first data row =
 * 2). Sensitive values are not exposed in logs/audit. Maps the V2-005 (V11)
 * table.
 */
@Entity
@Table(name = "import_session_row")
public class ImportSessionRow {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "import_session_id", nullable = false)
    private UUID importSessionId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @Column(name = "validated_data", nullable = false)
    private String validatedData;

    @Column(name = "issues", nullable = false)
    private String issues;

    public ImportSessionRow() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getImportSessionId() {
        return importSessionId;
    }

    public void setImportSessionId(UUID importSessionId) {
        this.importSessionId = importSessionId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getValidatedData() {
        return validatedData;
    }

    public void setValidatedData(String validatedData) {
        this.validatedData = validatedData;
    }

    public String getIssues() {
        return issues;
    }

    public void setIssues(String issues) {
        this.issues = issues;
    }
}
