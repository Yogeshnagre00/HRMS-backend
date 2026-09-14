package com.example.HRMS.statutory.entity;

/**
 * Lifecycle status of a {@link StatutoryRuleVersionSet} (Data Model 5.2).
 *
 * <p>A rule version set is a verified release input. {@code VERIFIED} sets are
 * the ones payroll may reference; {@code SUPERSEDED} sets are retained for
 * historical reproducibility; {@code DRAFT} sets are not yet usable. This slice
 * does not create or verify sets — it only identifies and references them.
 */
public enum RuleVersionSetStatus {
    VERIFIED,
    SUPERSEDED,
    DRAFT
}
