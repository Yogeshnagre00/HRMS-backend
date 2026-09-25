package com.example.HRMS.statutory.service;

import com.example.HRMS.statutory.entity.StatutoryRuleType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The traceable result of resolving one statutory rule (PF/PT/TDS) for a payroll
 * run (Phase 4 — Statutory Calculation Foundation).
 *
 * <p>Captures WHICH rule/version was (or was not) resolved and WHY, so a payroll
 * calculation is later explainable and reproducible. It carries no statutory
 * amount and no rule values — computing an amount is only permissible when
 * {@link #status()} is {@link StatutoryResolutionStatus#RESOLVED_VERIFIED} and a
 * {@link #ruleId()} + {@link #rulePayload()} are present.
 *
 * @param type          statutory type (PF/PT/TDS)
 * @param status        explicit resolution outcome
 * @param ruleVersionSetId the run's fixed rule version set (traceability)
 * @param ruleId        the resolved rule row id, when RESOLVED_VERIFIED; else null
 * @param rulePayload   the VERIFIED rule's JSON payload, when RESOLVED_VERIFIED; else null
 * @param effectiveFrom resolved rule effective_from, when a rule row matched; else null
 * @param effectiveTo   resolved rule effective_to (null = open-ended), when matched
 * @param dimension     the resolution dimension (PT state or TDS financial year), when relevant
 * @param reason        stable machine-readable reason describing the outcome
 */
public record ResolvedStatutoryRule(
        StatutoryRuleType type,
        StatutoryResolutionStatus status,
        UUID ruleVersionSetId,
        UUID ruleId,
        String rulePayload,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String dimension,
        String reason) {

    /** Whether a statutory amount may be computed from this resolution. */
    public boolean isComputable() {
        return status == StatutoryResolutionStatus.RESOLVED_VERIFIED
                && ruleId != null && rulePayload != null;
    }

    /** Whether this resolution is a blocking condition for approval/lock (later slice). */
    public boolean isBlocking() {
        return status == StatutoryResolutionStatus.UNCONFIRMED
                || status == StatutoryResolutionStatus.UNSUPPORTED
                || status == StatutoryResolutionStatus.RELEASE_DATA_MISSING;
    }

    static ResolvedStatutoryRule of(StatutoryRuleType type, StatutoryResolutionStatus status,
                                    UUID ruleVersionSetId, String dimension, String reason) {
        return new ResolvedStatutoryRule(type, status, ruleVersionSetId, null, null, null, null,
                dimension, reason);
    }
}
