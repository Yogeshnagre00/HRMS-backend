package com.example.HRMS.statutory.entity;

/**
 * The five Professional Tax states supported by v0 (V2-009.2 PT closure).
 *
 * <p>Used to key a {@link StatutoryPtRule} to a state. Structure only — no slab
 * values are implied. Tamil Nadu additionally supports a local body and a
 * half-yearly period (see {@link PtRulePeriodicity}), which is why PT rules are
 * stored per state rather than as a single shared model.
 */
public enum PtRuleState {
    MAHARASHTRA,
    KARNATAKA,
    TAMIL_NADU,
    TELANGANA,
    WEST_BENGAL
}
