package com.example.HRMS.statutory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Statutory Configuration API (API spec §8).
 * Persistence entities are never exposed. Structural validation is expressed
 * here; cross-field and business rules (PT supported state, PF registration
 * requirements, effective-date ordering, rule-version existence) are enforced
 * in the service layer.
 */
public final class StatutoryDtos {

    private StatutoryDtos() {
    }

    /**
     * Create/update the effective statutory configuration for the v0 legal
     * entity. Enum-typed fields are accepted as strings and matched with a
     * pattern so an invalid value yields a deterministic 400 VALIDATION_ERROR
     * rather than a parse failure.
     */
    public record UpsertStatutoryConfigurationRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull @Pattern(regexp = "YES|NO|UNCONFIRMED",
                    message = "pfApplicability must be YES, NO or UNCONFIRMED")
            String pfApplicability,
            @Pattern(regexp = "REGISTERED|NOT_REGISTERED|VOLUNTARY_COVERAGE",
                    message = "pfRegistrationStatus must be REGISTERED, NOT_REGISTERED or "
                            + "VOLUNTARY_COVERAGE")
            String pfRegistrationStatus,
            @Size(max = 50) String pfRegistrationNumber,
            @Size(max = 100) String ptState,
            @NotNull @Pattern(regexp = "NEW_REGIME_AUTOMATIC_V0",
                    message = "tdsPolicy must be NEW_REGIME_AUTOMATIC_V0")
            String tdsPolicy,
            @NotNull UUID ruleVersionSetId) {
    }

    public record StatutoryConfigurationResponse(
            UUID id,
            UUID legalEntityId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String pfApplicability,
            String pfRegistrationStatus,
            String pfRegistrationNumber,
            String ptState,
            String tdsPolicy,
            UUID ruleVersionSetId,
            UUID createdBy,
            LocalDateTime createdAt) {
    }

    /** Read-only view of a verified statutory rule version set (metadata only). */
    public record RuleVersionSetResponse(
            UUID id,
            String jurisdiction,
            String pfRuleVersion,
            String ptRuleVersion,
            String tdsRuleVersion,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String sourceReference,
            LocalDateTime verifiedAt,
            String status) {
    }

    // --- Phase 2: versioned statutory rule-value store (SUPER_ADMIN) ---------

    /**
     * Create a DRAFT PF rule under a rule version set. {@code rulePayload} is the
     * structured PF values as JSON (optional while DRAFT). Provenance/source
     * fields are optional at DRAFT but required to VERIFY. No values are implied
     * or defaulted by the server.
     */
    public record CreatePfRuleRequest(
            @NotNull UUID ruleVersionSetId,
            @NotNull @Size(max = 100) String jurisdiction,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 255) String authority,
            @Size(max = 500) String sourceDocument,
            @Size(max = 1000) String sourceUrl,
            LocalDate verificationDate,
            String rulePayload) {
    }

    /** Create a DRAFT PT rule for a state under a rule version set. */
    public record CreatePtRuleRequest(
            @NotNull UUID ruleVersionSetId,
            @NotNull @Size(max = 100) String jurisdiction,
            @NotNull @Pattern(regexp = "MAHARASHTRA|KARNATAKA|TAMIL_NADU|TELANGANA|WEST_BENGAL",
                    message = "ptState must be one of the five supported states")
            String ptState,
            @Size(max = 100) String localBody,
            @NotNull @Pattern(regexp = "MONTHLY|HALF_YEARLY",
                    message = "periodicity must be MONTHLY or HALF_YEARLY")
            String periodicity,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 255) String authority,
            @Size(max = 500) String sourceDocument,
            @Size(max = 1000) String sourceUrl,
            LocalDate verificationDate,
            String rulePayload) {
    }

    /** Create a DRAFT TDS rule (New Regime automatic v0) under a rule version set. */
    public record CreateTdsRuleRequest(
            @NotNull UUID ruleVersionSetId,
            @NotNull @Size(max = 100) String jurisdiction,
            @NotNull @Pattern(regexp = "\\d{4}-\\d{2}",
                    message = "financialYear must be canonical YYYY-YY")
            String financialYear,
            @NotNull @Pattern(regexp = "NEW_REGIME_AUTOMATIC_V0",
                    message = "taxRegime must be NEW_REGIME_AUTOMATIC_V0")
            String taxRegime,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 255) String authority,
            @Size(max = 500) String sourceDocument,
            @Size(max = 1000) String sourceUrl,
            LocalDate verificationDate,
            String rulePayload) {
    }

    /**
     * Update the mutable fields of a DRAFT rule (any type). Only permitted while
     * the rule is DRAFT; VERIFIED/SUPERSEDED rules are immutable. Null fields are
     * treated as "clear" for optional provenance; effectiveFrom is required.
     */
    public record UpdateDraftRuleRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 255) String authority,
            @Size(max = 500) String sourceDocument,
            @Size(max = 1000) String sourceUrl,
            LocalDate verificationDate,
            String rulePayload) {
    }

    /**
     * Read-only view of a single statutory rule value row (PF/PT/TDS). Common
     * fields plus optional type-specific fields ({@code ptState}, {@code localBody},
     * {@code periodicity}, {@code financialYear}, {@code taxRegime}). The raw
     * {@code rulePayload} is returned so a release authority can review it.
     */
    public record StatutoryRuleResponse(
            UUID id,
            UUID ruleVersionSetId,
            String ruleType,
            String jurisdiction,
            String ptState,
            String localBody,
            String periodicity,
            String financialYear,
            String taxRegime,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String authority,
            String sourceDocument,
            String sourceUrl,
            LocalDate verificationDate,
            String releaseStatus,
            String rulePayload,
            UUID createdBy,
            LocalDateTime createdAt,
            UUID verifiedBy,
            LocalDateTime verifiedAt) {
    }
}
