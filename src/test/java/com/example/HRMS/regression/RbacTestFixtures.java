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
        jdbcTemplate.update("DELETE FROM payroll_run");
        jdbcTemplate.update("DELETE FROM statutory_rule_version_set");
        jdbcTemplate.update("DELETE FROM import_session_row");
        jdbcTemplate.update("DELETE FROM import_session");
        jdbcTemplate.update("DELETE FROM compensation_record");
        jdbcTemplate.update("DELETE FROM employee_leave_balance");
        jdbcTemplate.update("DELETE FROM employee_opening_tax_state");
        jdbcTemplate.update("DELETE FROM employee_bank_account");
        jdbcTemplate.update("DELETE FROM employee");
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

    /** Insert an ACTIVE legal entity for the given company; returns its id. */
    public UUID insertLegalEntity(UUID companyId, String legalName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO legal_entity (id, company_id, legal_name, country_code, pan, "
                        + "financial_year_start, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'IN', 'AAACA1234A', DATE '2026-04-01', 'ACTIVE', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id.toString(), companyId.toString(), legalName);
        return id;
    }

    /** Insert an employee directly for tests; returns its surrogate id. */
    public UUID insertEmployee(UUID legalEntityId, String businessEmployeeId, String fullName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO employee (id, legal_entity_id, employee_id, full_name, joining_date, "
                        + "employment_type, pan, tax_regime, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, DATE '2026-04-01', 'FULL_TIME', 'AAAAA0000A', "
                        + "'NEW_REGIME', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id.toString(), legalEntityId.toString(), businessEmployeeId, fullName);
        return id;
    }

    /**
     * Insert an employee with explicit joining/exit dates (ISO {@code yyyy-MM-dd};
     * exit may be null). Used by payroll employee-population boundary tests.
     */
    public UUID insertEmployeeWithPeriod(UUID legalEntityId, String businessEmployeeId,
                                         String fullName, String joiningDate, String exitDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO employee (id, legal_entity_id, employee_id, full_name, joining_date, "
                        + "exit_date, employment_type, pan, tax_regime, status, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, 'FULL_TIME', 'AAAAA0000A', "
                        + "'NEW_REGIME', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id.toString(), legalEntityId.toString(), businessEmployeeId, fullName,
                joiningDate, exitDate);
        return id;
    }

    /** The id of the single ACTIVE company (created via API in tests). */
    public UUID activeCompanyId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM company WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                UUID.class);
    }

    /** The id of the single ACTIVE legal entity (created via API in tests). */
    public UUID activeLegalEntityId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM legal_entity WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                UUID.class);
    }

    /**
     * Insert a statutory rule version set for tests. The rule-version identifiers
     * and source reference are non-authoritative TEST placeholders only — they
     * are not verified statutory constants and never appear in production seeds.
     */
    public UUID insertRuleVersionSet(String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO statutory_rule_version_set (id, jurisdiction, pf_rule_version, "
                        + "pt_rule_version, tds_rule_version, effective_from, effective_to, "
                        + "source_reference, verified_at, status) "
                        + "VALUES (?, 'IN', 'TEST-PF-0', 'TEST-PT-0', 'TEST-TDS-0', "
                        + "DATE '2026-04-01', NULL, 'TEST-FIXTURE-NOT-AUTHORITATIVE', "
                        + "CURRENT_TIMESTAMP, ?)",
                id.toString(), status);
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
