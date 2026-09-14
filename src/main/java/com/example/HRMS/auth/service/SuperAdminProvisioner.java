package com.example.HRMS.auth.service;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.rbac.entity.Role;
import com.example.HRMS.rbac.entity.UserRole;
import com.example.HRMS.rbac.repository.RoleRepository;
import com.example.HRMS.rbac.repository.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional creator for the bootstrap Super Admin, separated from the
 * {@link SuperAdminBootstrap} runner so {@code @Transactional} is applied via the
 * Spring proxy (not self-invoked). The admin + role assignment are committed as a
 * unit; the caller records the audit event afterwards so the audit actor foreign
 * key references a committed user.
 */
@Component
public class SuperAdminProvisioner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminProvisioner.class);
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminProvisioner(AppUserRepository userRepository,
                                 RoleRepository roleRepository,
                                 UserRoleRepository userRoleRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create the admin and its SUPER_ADMIN assignment in one committed
     * transaction. Returns the new admin id, or empty if nothing was created
     * (already present, or the SUPER_ADMIN role is missing).
     */
    @Transactional
    public Optional<UUID> createIfAbsent(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            log.info("Super Admin bootstrap: user '{}' already exists; no action taken.", username);
            return Optional.empty();
        }

        Role superAdmin = roleRepository.findByCode(SUPER_ADMIN_CODE).orElse(null);
        if (superAdmin == null) {
            log.warn("Super Admin bootstrap: role {} not found; skipping bootstrap.", SUPER_ADMIN_CODE);
            return Optional.empty();
        }

        AppUser admin = new AppUser();
        admin.setId(UUID.randomUUID());
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setScopeType(ScopeType.PLATFORM);
        admin.setCompanyId(null);
        admin.setMfaEnabled(false);
        admin.setTokenVersion(0);
        admin.setMustChangePassword(true); // bootstrap password must be rotated
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        userRoleRepository.save(new UserRole(UUID.randomUUID(), admin.getId(), superAdmin.getId()));

        log.info("Super Admin bootstrap: created platform admin '{}' (must change password on first login).",
                username);
        return Optional.of(admin.getId());
    }
}
