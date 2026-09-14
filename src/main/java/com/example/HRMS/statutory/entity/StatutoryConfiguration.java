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
 * Effective-dated statutory applicability and policy for a legal entity (table
 * {@code statutory_configuration}; Data Model 5.1).
 *
 * <p>Holds the <em>explicit</em> PF/PT/TDS applicability state and a reference to
 * a verified {@link StatutoryRuleVersionSet}. It never stores or infers statutory
 * rates. Applicability is explicit and includes {@code UNCONFIRMED}, which must
 * never be silently treated as {@code NO}/{@code YES} (Business Rules §7). Maps
 * the existing V0-002 table (with the {@code created_by → app_user} FK finalized
 * in V0-003) — no schema change.
 */
@Entity
@Table(name = "statutory_configuration")
public class StatutoryConfiguration {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "pf_applicability", nullable = false)
    private PfApplicability pfApplicability;

    @Enumerated(EnumType.STRING)
    @Column(name = "pf_registration_status")
    private PfRegistrationStatus pfRegistrationStatus;

    @Column(name = "pf_registration_number")
    private String pfRegistrationNumber;

    @Column(name = "pt_state")
    private String ptState;

    @Enumerated(EnumType.STRING)
    @Column(name = "tds_policy", nullable = false)
    private TdsPolicy tdsPolicy;

    @Column(name = "rule_version_set_id", nullable = false)
    private UUID ruleVersionSetId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public StatutoryConfiguration() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(UUID legalEntityId) {
        this.legalEntityId = legalEntityId;
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

    public PfApplicability getPfApplicability() {
        return pfApplicability;
    }

    public void setPfApplicability(PfApplicability pfApplicability) {
        this.pfApplicability = pfApplicability;
    }

    public PfRegistrationStatus getPfRegistrationStatus() {
        return pfRegistrationStatus;
    }

    public void setPfRegistrationStatus(PfRegistrationStatus pfRegistrationStatus) {
        this.pfRegistrationStatus = pfRegistrationStatus;
    }

    public String getPfRegistrationNumber() {
        return pfRegistrationNumber;
    }

    public void setPfRegistrationNumber(String pfRegistrationNumber) {
        this.pfRegistrationNumber = pfRegistrationNumber;
    }

    public String getPtState() {
        return ptState;
    }

    public void setPtState(String ptState) {
        this.ptState = ptState;
    }

    public TdsPolicy getTdsPolicy() {
        return tdsPolicy;
    }

    public void setTdsPolicy(TdsPolicy tdsPolicy) {
        this.tdsPolicy = tdsPolicy;
    }

    public UUID getRuleVersionSetId() {
        return ruleVersionSetId;
    }

    public void setRuleVersionSetId(UUID ruleVersionSetId) {
        this.ruleVersionSetId = ruleVersionSetId;
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
}
