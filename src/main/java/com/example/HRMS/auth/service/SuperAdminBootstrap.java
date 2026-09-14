package com.example.HRMS.auth.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.security.ScopeType;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the initial platform Super Admin on startup, once.
 *
 * <p>Idempotent: if a user with the configured bootstrap username already exists,
 * it does nothing (no password reset on restart). Otherwise it creates an ACTIVE
 * {@code PLATFORM} user with a securely hashed password, flagged
 * {@code mustChangePassword} (the initial password is a bootstrap credential),
 * assigns the {@code SUPER_ADMIN} role, and records an audit event.
 *
 * <p>Credentials come from configuration/environment ({@link BootstrapProperties})
 * only — there is no hardcoded password in source, and the password is never
 * logged. If no bootstrap password is provided the bootstrap is skipped.
 *
 * <p>User creation is committed by {@link SuperAdminProvisioner} first; the audit
 * event is recorded afterwards so the audit log's {@code actor_user_id} foreign
 * key (referencing the new admin) is satisfied.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final BootstrapProperties properties;
    private final SuperAdminProvisioner provisioner;
    private final AuditService auditService;

    public SuperAdminBootstrap(BootstrapProperties properties,
                               SuperAdminProvisioner provisioner,
                               AuditService auditService) {
        this.properties = properties;
        this.provisioner = provisioner;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getPassword() == null || properties.getPassword().isBlank()) {
            // No committed/default password: require it from the environment.
            log.warn("Super Admin bootstrap: no bootstrap password provided "
                    + "(set HRMS_BOOTSTRAP_ADMIN_PASSWORD); skipping bootstrap.");
            return;
        }

        Optional<UUID> createdAdminId = provisioner.createIfAbsent(
                properties.getUsername(), properties.getEmail(), properties.getPassword());

        // Recorded after the admin is committed so the audit actor FK is satisfied.
        createdAdminId.ifPresent(adminId -> auditService.record(AuditEvent.of(
                adminId, ScopeType.PLATFORM,
                AuditActions.USER_CREATED, AuditActions.ENTITY_USER, adminId, "SUCCESS")));
    }
}
