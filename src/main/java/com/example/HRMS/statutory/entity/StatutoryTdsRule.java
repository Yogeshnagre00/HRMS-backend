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
 * Versioned TDS statutory rule values for a {@link StatutoryRuleVersionSet}
 * (table {@code statutory_tds_rule}; Phase 2).
 *
 * <p>v0 supports New-Regime automatic TDS only ({@code taxRegime =
 * NEW_REGIME_AUTOMATIC_V0}). Structured slabs/standard-deduction/rebate/cess/
 * surcharge/rounding live in {@code rulePayload} (JSON-as-TEXT) as verified
 * release data keyed to a {@code financialYear} — never hardcoded in Java.
 * {@code VERIFIED}/{@code SUPERSEDED} rows are immutable (service-enforced).
 * Maps the V19 table.
 */
@Entity
@Table(name = "statutory_tds_rule")
public class StatutoryTdsRule {

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

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(name = "tax_regime", nullable = false)
    private String taxRegime;

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

    public StatutoryTdsRule() {
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

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
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
