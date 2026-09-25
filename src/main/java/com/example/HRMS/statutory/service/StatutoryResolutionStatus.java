package com.example.HRMS.statutory.service;

/**
 * Outcome of resolving a statutory rule (PF/PT/TDS) for a payroll run
 * (Phase 4 — Statutory Calculation Foundation).
 *
 * <p>Resolution is explicit and never silently substitutes a zero result for an
 * unresolved state (Business Rules §7; V2-009 audits). A statutory amount may be
 * computed ONLY when the outcome is {@link #RESOLVED_VERIFIED}. Every other
 * outcome means "do not compute a legally-meaningful statutory amount" — the
 * amount stays {@code NULL} (not {@code 0.00}) and the condition is surfaced for
 * Health Check in a later slice.
 *
 * <p>{@code RESOLVED_VERIFIED} additionally requires that a VERIFIED rule payload
 * with the actual statutory values exists. Because verified statutory release
 * data is not yet present in v0 (the rule tables are empty), an applicable PF/PT/
 * TDS currently resolves to {@link #RELEASE_DATA_MISSING} rather than a computed
 * value.
 */
public enum StatutoryResolutionStatus {

    /**
     * A VERIFIED rule covering the payroll month was found and applicability is
     * confirmed. Only in this state may a statutory amount be computed. (Not
     * reachable in v0 until verified release data exists.)
     */
    RESOLVED_VERIFIED,

    /**
     * The statutory item is explicitly not applicable per configuration (e.g. PF
     * applicability = NO). The amount is a genuine, contract-defined absence
     * (still represented as NULL at the pre-statutory stage), not an unresolved
     * state.
     */
    NOT_APPLICABLE,

    /**
     * Applicability is explicitly unconfirmed (e.g. PF applicability = UNCONFIRMED)
     * — blocking; never treated as YES or NO.
     */
    UNCONFIRMED,

    /**
     * The scenario/jurisdiction is not supported by v0 (e.g. an unsupported PT
     * state, or Old-Regime automatic TDS) — blocking; never silently zero.
     */
    UNSUPPORTED,

    /**
     * Applicability is confirmed/supported but no VERIFIED statutory rule with
     * values is available for the payroll month (no rule row, DRAFT only, or no
     * effective coverage) — blocking; never computed with guessed values.
     */
    RELEASE_DATA_MISSING
}
