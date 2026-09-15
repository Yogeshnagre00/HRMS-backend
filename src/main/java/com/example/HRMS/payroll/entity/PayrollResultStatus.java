package com.example.HRMS.payroll.entity;

/**
 * Validity status of a {@link PayrollEmployeeResult} (Data Model 10.2).
 *
 * <p>Describes the validity of the employee's calculated result — distinct from
 * {@link PayrollRunStatus} (the run lifecycle). At the non-statutory
 * (pre-statutory) stage it reflects only non-statutory calculation validity
 * (§10.2.1): {@code VALID} normal; {@code REVIEW} a surfaced non-blocking
 * concern; {@code BLOCKED} a blocking calculation condition.
 */
public enum PayrollResultStatus {
    VALID,
    REVIEW,
    BLOCKED
}
