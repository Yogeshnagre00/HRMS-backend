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
        jdbcTemplate.update("DELETE FROM variable_earning");
        jdbcTemplate.update("DELETE FROM arrear");
        jdbcTemplate.update("DELETE FROM payroll_day_result");
        jdbcTemplate.update("DELETE FROM payroll_result_line");
        jdbcTemplate.update("DELETE FROM payroll_employee_result");
        jdbcTemplate.update("DELETE FROM payroll_run");
        jdbcTemplate.update("DELETE FROM statutory_pf_rule");
        jdbcTemplate.update("DELETE FROM statutory_pt_rule");
        jdbcTemplate.update("DELETE FROM statutory_tds_rule");
        jdbcTemplate.update("DELETE FROM statutory_rule_version_set");
        jdbcTemplate.update("DELETE FROM import_session_row");
        jdbcTemplate.update("DELETE FROM import_session");
        jdbcTemplate.update("DELETE FROM compensation_record");
        jdbcTemplate.update("DELETE FROM leave_entry");
        jdbcTemplate.update("DELETE FROM attendance_exception");
        jdbcTemplate.update("DELETE FROM work_calendar_assignment");
        jdbcTemplate.update("DELETE FROM work_calendar");
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

    /**
     * Insert a PayrollRun directly for tests with an explicit status, so
     * payroll-input lifecycle tests can exercise statuses not yet reachable
     * through the public API. Uses a fixed payroll month/FY/calculation version.
     */
    public UUID insertPayrollRun(UUID legalEntityId, UUID ruleVersionSetId, UUID createdBy,
                                 String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO payroll_run (id, legal_entity_id, payroll_month, financial_year, "
                        + "status, calculation_version, rule_version_set_id, created_by, "
                        + "created_at) VALUES (?, ?, DATE '2026-06-01', '2026-27', ?, '0', ?, ?, "
                        + "CURRENT_TIMESTAMP)",
                id.toString(), legalEntityId.toString(), status, ruleVersionSetId.toString(),
                createdBy.toString());
        return id;
    }

    /**
     * Insert a PayrollRun for an explicit payroll month/FY (used by payroll
     * calculation tests that need a specific month window). Status and version
     * are given by the caller.
     */
    public UUID insertPayrollRun(UUID legalEntityId, UUID ruleVersionSetId, UUID createdBy,
                                 String status, String payrollMonth, String financialYear,
                                 String calculationVersion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO payroll_run (id, legal_entity_id, payroll_month, financial_year, "
                        + "status, calculation_version, rule_version_set_id, created_by, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id.toString(), legalEntityId.toString(), payrollMonth, financialYear, status,
                calculationVersion, ruleVersionSetId.toString(), createdBy.toString());
        return id;
    }

    /** Insert a v0 standard 5-day work calendar (Mon-Fri worked, Sat/Sun off); returns id. */
    public UUID insertWorkCalendar(UUID legalEntityId, String name, String effectiveFrom,
                                   String effectiveTo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO work_calendar (id, legal_entity_id, name, effective_from, "
                        + "effective_to, monday_to_friday, saturday_sunday_weekly_off, status) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE, TRUE, 'ACTIVE')",
                id.toString(), legalEntityId.toString(), name, effectiveFrom, effectiveTo);
        return id;
    }

    /** Assign a work calendar to an employee for an effective range; returns id. */
    public UUID insertWorkCalendarAssignment(UUID employeeId, UUID workCalendarId,
                                             String effectiveFrom, String effectiveTo,
                                             UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO work_calendar_assignment (id, employee_id, work_calendar_id, "
                        + "effective_from, effective_to, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                id.toString(), employeeId.toString(), workCalendarId.toString(), effectiveFrom,
                effectiveTo, createdBy.toString());
        return id;
    }

    /** Insert an effective-dated compensation record; returns id. */
    public UUID insertCompensation(UUID employeeId, String effectiveFrom, String effectiveTo,
                                   String ctc, String basic, String hra, String other,
                                   UUID createdBy) {
        // DA defaults to 0 for callers that predate the DA component (Phase 3).
        return insertCompensation(employeeId, effectiveFrom, effectiveTo, ctc, basic, hra,
                "0.00", other, createdBy);
    }

    /** Insert an effective-dated compensation record including DA; returns id. */
    public UUID insertCompensation(UUID employeeId, String effectiveFrom, String effectiveTo,
                                   String ctc, String basic, String hra, String da, String other,
                                   UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compensation_record (id, employee_id, effective_from, effective_to, "
                        + "ctc_monthly, basic_monthly, hra_monthly, da_monthly, "
                        + "other_fixed_allowances_monthly, source, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, CURRENT_TIMESTAMP)",
                id.toString(), employeeId.toString(), effectiveFrom, effectiveTo, ctc, basic, hra,
                da, other, createdBy.toString());
        return id;
    }

    /** Insert a RECORDED leave entry (treatment PAID_LEAVE or UNPAID_LOP_LEAVE); returns id. */
    public UUID insertLeaveEntry(UUID employeeId, String leaveDate, String treatment,
                                 String quantity, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO leave_entry (id, employee_id, leave_date, treatment, quantity, "
                        + "status, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'RECORDED', ?, CURRENT_TIMESTAMP)",
                id.toString(), employeeId.toString(), leaveDate, treatment, quantity,
                createdBy.toString());
        return id;
    }

    /** Insert an attendance exception (FULL_DAY_ABSENCE/HALF_DAY/LOP); returns id. */
    public UUID insertAttendanceException(UUID employeeId, String attendanceDate, String type,
                                          String quantity, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO attendance_exception (id, employee_id, attendance_date, "
                        + "exception_type, quantity, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id.toString(), employeeId.toString(), attendanceDate, type, quantity,
                createdBy.toString());
        return id;
    }

    /** Insert a variable earning for an employee + run; returns id. */
    public UUID insertVariableEarning(UUID employeeId, UUID payrollRunId, String description,
                                      String amount, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO variable_earning (id, employee_id, payroll_run_id, description, "
                        + "amount, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, "
                        + "CURRENT_TIMESTAMP)",
                id.toString(), employeeId.toString(), payrollRunId.toString(), description, amount,
                createdBy.toString());
        return id;
    }

    /** Insert an arrear for an employee + run; returns id. */
    public UUID insertArrear(UUID employeeId, UUID payrollRunId, String amount,
                             String periodReference, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO arrear (id, employee_id, payroll_run_id, amount, period_reference, "
                        + "reason, created_by, created_at) VALUES (?, ?, ?, ?, ?, 'revision', ?, "
                        + "CURRENT_TIMESTAMP)",
                id.toString(), employeeId.toString(), payrollRunId.toString(), amount,
                periodReference, createdBy.toString());
        return id;
    }

    /** Insert a PF statutory rule row (Phase 4 resolver tests); returns id. */
    public UUID insertPfRule(UUID ruleVersionSetId, String jurisdiction, String effectiveFrom,
                             String effectiveTo, String releaseStatus, String payload,
                             UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO statutory_pf_rule (id, rule_version_set_id, rule_type, jurisdiction, "
                        + "effective_from, effective_to, authority, source_document, source_url, "
                        + "verification_date, release_status, rule_payload, created_by, created_at) "
                        + "VALUES (?, ?, 'PF', ?, ?, ?, 'TEST-AUTH', 'TEST-DOC', 'https://test', "
                        + "DATE '2026-06-29', ?, ?, ?, CURRENT_TIMESTAMP)",
                id.toString(), ruleVersionSetId.toString(), jurisdiction, effectiveFrom,
                effectiveTo, releaseStatus, payload, createdBy.toString());
        return id;
    }

    /** Insert a PT statutory rule row for a state (Phase 4 resolver tests); returns id. */
    public UUID insertPtRule(UUID ruleVersionSetId, String jurisdiction, String ptState,
                             String periodicity, String effectiveFrom, String effectiveTo,
                             String releaseStatus, String payload, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO statutory_pt_rule (id, rule_version_set_id, rule_type, jurisdiction, "
                        + "pt_state, periodicity, effective_from, effective_to, authority, "
                        + "source_document, source_url, verification_date, release_status, "
                        + "rule_payload, created_by, created_at) "
                        + "VALUES (?, ?, 'PT', ?, ?, ?, ?, ?, 'TEST-AUTH', 'TEST-DOC', "
                        + "'https://test', DATE '2026-06-29', ?, ?, ?, CURRENT_TIMESTAMP)",
                id.toString(), ruleVersionSetId.toString(), jurisdiction, ptState, periodicity,
                effectiveFrom, effectiveTo, releaseStatus, payload, createdBy.toString());
        return id;
    }

    /** Insert a TDS statutory rule row (Phase 4 resolver tests); returns id. */
    public UUID insertTdsRule(UUID ruleVersionSetId, String jurisdiction, String financialYear,
                              String effectiveFrom, String effectiveTo, String releaseStatus,
                              String payload, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO statutory_tds_rule (id, rule_version_set_id, rule_type, jurisdiction, "
                        + "financial_year, tax_regime, effective_from, effective_to, authority, "
                        + "source_document, source_url, verification_date, release_status, "
                        + "rule_payload, created_by, created_at) "
                        + "VALUES (?, ?, 'TDS', ?, ?, 'NEW_REGIME_AUTOMATIC_V0', ?, ?, "
                        + "'TEST-AUTH', 'TEST-DOC', 'https://test', DATE '2026-06-29', ?, ?, ?, "
                        + "CURRENT_TIMESTAMP)",
                id.toString(), ruleVersionSetId.toString(), jurisdiction, financialYear,
                effectiveFrom, effectiveTo, releaseStatus, payload, createdBy.toString());
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
