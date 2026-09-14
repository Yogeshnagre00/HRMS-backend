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
 * Verified statutory rule version set (table {@code statutory_rule_version_set};
 * Data Model 5.2).
 *
 * <p>Identifies a versioned, verified set of statutory rule references that
 * payroll calculations point at for reproducibility. It carries rule-version
 * <em>identifiers</em> and provenance metadata only — never concrete rates,
 * ceilings, wage bases or slabs (those are release-time verified inputs and are
 * out of the model per Data Model 5.2 and AGENTS §11). Maps the existing V0-002
 * table — no schema change. Read-only via the API in v0.
 */
@Entity
@Table(name = "statutory_rule_version_set")
public class StatutoryRuleVersionSet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "jurisdiction", nullable = false)
    private String jurisdiction;

    @Column(name = "pf_rule_version", nullable = false)
    private String pfRuleVersion;

    @Column(name = "pt_rule_version", nullable = false)
    private String ptRuleVersion;

    @Column(name = "tds_rule_version", nullable = false)
    private String tdsRuleVersion;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "source_reference", nullable = false)
    private String sourceReference;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RuleVersionSetStatus status;

    public StatutoryRuleVersionSet() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getPfRuleVersion() {
        return pfRuleVersion;
    }

    public void setPfRuleVersion(String pfRuleVersion) {
        this.pfRuleVersion = pfRuleVersion;
    }

    public String getPtRuleVersion() {
        return ptRuleVersion;
    }

    public void setPtRuleVersion(String ptRuleVersion) {
        this.ptRuleVersion = ptRuleVersion;
    }

    public String getTdsRuleVersion() {
        return tdsRuleVersion;
    }

    public void setTdsRuleVersion(String tdsRuleVersion) {
        this.tdsRuleVersion = tdsRuleVersion;
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

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public RuleVersionSetStatus getStatus() {
        return status;
    }

    public void setStatus(RuleVersionSetStatus status) {
        this.status = status;
    }
}
