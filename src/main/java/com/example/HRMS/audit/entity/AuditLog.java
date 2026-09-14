package com.example.HRMS.audit.entity;

import com.example.HRMS.common.security.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit record (table {@code audit_log}; Data Model 15.1).
 *
 * <p>Captures material and security-sensitive actions. It deliberately has no
 * field for secrets: passwords, password hashes, tokens and MFA secrets must
 * never be written here.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "before_snapshot")
    private String beforeSnapshot;

    @Column(name = "after_snapshot")
    private String afterSnapshot;

    @Column(name = "reason")
    private String reason;

    @Column(name = "request_id")
    private String requestId;

    public AuditLog() {
        // JPA + programmatic construction by the audit service
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(UUID legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public void setBeforeSnapshot(String beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(String afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
