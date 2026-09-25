package com.example.HRMS.statutory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Versioned Professional Tax statutory rule values for one state within a
 * {@link StatutoryRuleVersionSet} (table {@code statutory_pt_rule}; Phase 2).
 *
 * <p>Keyed per {@link PtRuleState}; {@code localBody} and {@link PtRulePeriodicity}
 * let Tamil Nadu express a half-yearly, local-body jurisdiction rather than a
 * monthly-state model. Structured slab/threshold values live in
 * {@code rulePayload} (JSON-as-TEXT) as verified release data — never hardcoded.
 * {@code VERIFIED}/{@code SUPERSEDED} rows are immutable (service-enforced).
 * Maps the V19 table.
 */
@Entity
@Table(name = "statutory_pt_rule")
public class StatutoryPtRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_version_set_id", nullable = false)
    private UUID ruleVersionSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private StatutoryRuleType ruleType;

    @Column(name = "jurisdiction", nullable = false)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(name = "pt_state", nullable = false)
    private PtRuleState ptState;

    @Column(name = "local_body")
    private String localBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false)
    private PtRulePeriodicity periodicity;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "authority")
    private String authority;

    @Column(name = "source_document")
    private String sourceDocument;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "verification_date")
    private LocalDate verificationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_status", nullable = false)
    private RuleVersionSetStatus releaseStatus;

    @Column(name = "rule_payload")
    private String rulePayload;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public StatutoryPtRule() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRuleVersionSetId() {
        return ruleVersionSetId;
    }

    public void setRuleVersionSetId(UUID ruleVersionSetId) {
        this.ruleVersionSetId = ruleVersionSetId;
    }

    public StatutoryRuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(StatutoryRuleType ruleType) {
        this.ruleType = ruleType;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public PtRuleState getPtState() {
        return ptState;
    }

    public void setPtState(PtRuleState ptState) {
        this.ptState = ptState;
    }

    public String getLocalBody() {
        return localBody;
    }

    public void setLocalBody(String localBody) {
        this.localBody = localBody;
    }

    public PtRulePeriodicity getPeriodicity() {
        return periodicity;
    }

    public void setPeriodicity(PtRulePeriodicity periodicity) {
        this.periodicity = periodicity;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getSourceDocument() {
        return sourceDocument;
    }

    public void setSourceDocument(String sourceDocument) {
        this.sourceDocument = sourceDocument;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDate getVerificationDate() {
        return verificationDate;
    }

    public void setVerificationDate(LocalDate verificationDate) {
        this.verificationDate = verificationDate;
    }

    public RuleVersionSetStatus getReleaseStatus() {
        return releaseStatus;
    }

    public void setReleaseStatus(RuleVersionSetStatus releaseStatus) {
        this.releaseStatus = releaseStatus;
    }

    public String getRulePayload() {
        return rulePayload;
    }

    public void setRulePayload(String rulePayload) {
        this.rulePayload = rulePayload;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(UUID verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
