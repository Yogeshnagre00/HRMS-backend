package com.example.HRMS.statutory.entity;

/**
 * Statutory collection/assessment periodicity of a {@link StatutoryPtRule}
 * (V2-009.2 PT closure).
 *
 * <p>Maharashtra, Karnataka, Telangana and West Bengal operate {@code MONTHLY};
 * Tamil Nadu is {@code HALF_YEARLY} (with local-body jurisdiction). This lets a
 * PT rule express its true statutory period rather than being forced into a
 * monthly model. Structure only — no values implied.
 */
public enum PtRulePeriodicity {
    MONTHLY,
    HALF_YEARLY
}
