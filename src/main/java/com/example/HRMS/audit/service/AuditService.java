package com.example.HRMS.audit.service;

import com.example.HRMS.audit.entity.AuditLog;
import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.common.security.ScopeType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backend-only writer for the append-only {@link AuditLog}.
 *
 * <p>Audit events originate from application modules — never from client input —
 * so there is no API to create arbitrary audit records. Callers must not pass
 * secrets (passwords, hashes, tokens, MFA secrets); the audit model has no field
 * for them and this service does not accept them.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record a security/material action. A new transaction so the audit entry
     * survives even when the surrounding operation rolls back (e.g. failed login).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActorUserId(event.actorUserId());
        log.setCompanyId(event.companyId());
        log.setLegalEntityId(null);
        log.setAction(event.action());
        log.setEntityType(event.entityType());
        log.setEntityId(event.entityId());
        log.setScopeType(event.scopeType() != null ? event.scopeType() : ScopeType.PLATFORM);
        log.setOutcome(event.outcome());
        log.setOccurredAt(LocalDateTime.now());
        log.setReason(event.reason());
        log.setRequestId(event.requestId());
        auditLogRepository.save(log);
    }

    /** Value describing an audit event. Deliberately contains no secret fields. */
    public record AuditEvent(
            UUID actorUserId,
            UUID companyId,
            ScopeType scopeType,
            String action,
            String entityType,
            UUID entityId,
            String outcome,
            String reason,
            String requestId) {

        public static AuditEvent of(UUID actorUserId, ScopeType scopeType, String action,
                                    String entityType, UUID entityId, String outcome) {
            return new AuditEvent(actorUserId, null, scopeType, action, entityType, entityId,
                    outcome, null, null);
        }
    }
}
