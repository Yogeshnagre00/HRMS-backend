package com.example.HRMS.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Opt-in PostgreSQL integration smoke test (task V0-002.5).
 *
 * <p>Proves the application can start against the REAL local PostgreSQL {@code hrms}
 * database and that Flyway has produced the expected schema. It is intentionally
 * isolated from the default H2 test suite:
 *
 * <ul>
 *   <li>It uses the {@code postgres-it} profile
 *       ({@code src/test/resources/application-postgres-it.properties}).</li>
 *   <li>It is skipped unless {@code HRMS_DB_USERNAME} is present in the environment,
 *       so {@code mvn clean test} never requires a PostgreSQL password or install.</li>
 * </ul>
 *
 * <h2>How to run</h2>
 * <pre>
 *   # PowerShell (Windows)
 *   $env:HRMS_DB_USERNAME = "hrms_app"
 *   $env:HRMS_DB_PASSWORD = "&lt;local password&gt;"
 *   mvn -Dtest=PostgresIntegrationSmokeTest test
 *
 *   # bash
 *   HRMS_DB_USERNAME=hrms_app HRMS_DB_PASSWORD='&lt;local password&gt;' \
 *     mvn -Dtest=PostgresIntegrationSmokeTest test
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
@EnabledIfEnvironmentVariable(named = "HRMS_DB_USERNAME", matches = ".+")
class PostgresIntegrationSmokeTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "company",
            "legal_entity",
            "statutory_rule_version_set",
            "statutory_configuration",
            "work_calendar",
            "work_calendar_assignment");

    private static final List<String> AUTH_RBAC_TABLES = List.of(
            "app_user",
            "role",
            "permission",
            "role_permission",
            "user_role",
            "revoked_token",
            "audit_log");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void databaseConnectionSucceeds() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo("PostgreSQL");
        }
    }

    @Test
    void currentDatabaseIsHrms() {
        String db = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        assertThat(db).isEqualTo("hrms");
    }

    @Test
    void applicationUsesDedicatedAppUserNotSuperuser() {
        String user = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        assertThat(user).isNotEqualTo("postgres");
    }

    @Test
    void flywayMigrationsAreApplied() {
        MigrationInfo[] applied = flyway.info().applied();

        List<String> appliedVersions = Arrays.stream(applied)
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion())
                .toList();
        // V1/V2 (foundation) and V3/V4 (auth/RBAC/audit + seed).
        assertThat(appliedVersions).contains("1", "2", "3", "4");

        assertThat(applied)
                .filteredOn(info -> info.getVersion() != null)
                .allSatisfy(info ->
                        assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
    }

    @Test
    void allExpectedTablesExist() throws Exception {
        List<String> allTables = new java.util.ArrayList<>(EXPECTED_TABLES);
        allTables.addAll(AUTH_RBAC_TABLES);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : allTables) {
                try (ResultSet rs = meta.getTables(null, null, table, new String[] {"TABLE"})) {
                    assertThat(rs.next())
                            .as("table %s should exist in the hrms database", table)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void rbacSeedIsPresent() {
        Integer roles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role WHERE code IN "
                        + "('SUPER_ADMIN','COMPANY_ADMIN','PAYROLL_ADMIN','EMPLOYEE','MANAGER')",
                Integer.class);
        assertThat(roles).isEqualTo(5);

        Integer permissions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permission", Integer.class);
        assertThat(permissions).isGreaterThanOrEqualTo(9);

        Integer mappings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permission", Integer.class);
        assertThat(mappings).isGreaterThan(0);

        // Reserved roles must not be assignable and must have no permissions.
        Integer reservedAssignable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role WHERE code IN ('EMPLOYEE','MANAGER') AND assignable = TRUE",
                Integer.class);
        assertThat(reservedAssignable).isZero();
    }
}
