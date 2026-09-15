package com.example.HRMS.payroll.entity;

/**
 * Lifecycle status of a {@link PayrollRun} (Data Model 10.1; Business Rules
 * 10.1; API 17).
 *
 * <p>The authoritative payroll lifecycle is
 * {@code DRAFT -> CALCULATED -> HEALTH_CHECK -> REVIEW -> APPROVED -> LOCKED ->
 * OUTPUTS}. The V2-007 Payroll Run Foundation creates only {@code DRAFT} runs;
 * the later slices (calculation, health check, approval, lock, outputs) own the
 * transitions. All values are declared so the persisted CHECK domain and the
 * enum stay aligned, but no transition logic is implemented in this slice.
 */
public enum PayrollRunStatus {
    DRAFT,
    CALCULATED_PRE_STATUTORY,
    CALCULATED,
    HEALTH_CHECK,
    REVIEW,
    APPROVED,
    LOCKED,
    OUTPUTS
}
