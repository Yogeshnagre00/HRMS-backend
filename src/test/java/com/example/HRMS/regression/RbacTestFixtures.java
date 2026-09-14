package com.example.HRMS.regression;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.rbac.entity.UserRole;
import com.example.HRMS.rbac.repository.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Deterministic, test-only provisioning helpers for the RBAC regression suite.
 *
 * <p>Reuses the seeded V4 role ids and creates companies/users directly so tests
 * do not depend on any pre-existing database records. All provisioning is within
 * the test database; no real credentials or secrets are used.
 */
@Component
public class RbacTestFixtures {

    // Seeded role ids from V4__auth_rbac_seed.sql.
    public static final UUID ROLE_SUPER_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID ROLE_COMPANY_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000102");
    public static final UUID ROLE_PAYROLL_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000103");
    public static final UUID ROLE_EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-000000000104");
    public static final UUID ROLE_MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000105");

    public static final String PASSWORD = "Str0ngPass!";

    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public RbacTestFixtures(AppUserRepository userRepository,
                            UserRoleRepository userRoleRepository,
                            PasswordEncoder passwordEncoder,
                            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Remove all rows that reference app_user/company in FK-safe order (shared H2). */
    public void resetIdentityData() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM revoked_token");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM statutory_configuration");
        jdbcTemplate.update("DELETE FROM legal_entity");
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM company");
    }

    public UUID insertCompany(String name) {
        return insertCompany(name, "ACTIVE");
    }

    public UUID insertCompany(String name, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO company (id, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id.toString(), name, status);
        return id;
    }

    public UUID createUser(String username, ScopeType scope, UUID companyId, UserStatus status) {
        return createUser(username, scope, companyId, status, false, null, false);
    }

    public UUID createUser(String username, ScopeType scope, UUID companyId, UserStatus status,
                           boolean mfaEnabled, String mfaSecret, boolean mustChangePassword) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(status);
        user.setScopeType(scope);
        user.setCompanyId(companyId);
        user.setMfaEnabled(mfaEnabled);
        user.setMfaSecret(mfaSecret);
        user.setTokenVersion(0);
        user.setMustChangePassword(mustChangePassword);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user).getId();
    }

    public void assignRole(UUID userId, UUID roleId) {
        userRoleRepository.save(new UserRole(UUID.randomUUID(), userId, roleId));
    }
}
