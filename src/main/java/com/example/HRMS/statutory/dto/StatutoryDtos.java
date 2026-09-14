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
}
