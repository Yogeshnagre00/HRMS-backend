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
    void flywayV1AndV2AreApplied() {
        MigrationInfo[] applied = flyway.info().applied();

        List<String> appliedVersions = Arrays.stream(applied)
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion())
                .toList();
        assertThat(appliedVersions).contains("1", "2");

        assertThat(applied)
                .filteredOn(info -> info.getVersion() != null
                        && ("1".equals(info.getVersion().getVersion())
                        || "2".equals(info.getVersion().getVersion())))
                .allSatisfy(info ->
                        assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
    }

    @Test
    void allSixV0002TablesExist() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : EXPECTED_TABLES) {
                try (ResultSet rs = meta.getTables(null, null, table, new String[] {"TABLE"})) {
                    assertThat(rs.next())
                            .as("table %s should exist in the hrms database", table)
                            .isTrue();
                }
            }
        }
    }
}
