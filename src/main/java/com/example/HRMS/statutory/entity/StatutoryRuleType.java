package com.example.HRMS.statutory.entity;

/**
 * Statutory rule category held in the versioned rule-value store (Phase 2).
 *
 * <p>Each rule row is exactly one of these types. Determines which child table
 * ({@code statutory_pf_rule} / {@code statutory_pt_rule} / {@code statutory_tds_rule})
 * carries the structured values. This is release-data structure only; no
 * statutory values are implied by the enum itself.
 */
public enum StatutoryRuleType {
    PF,
    PT,
    TDS
}
