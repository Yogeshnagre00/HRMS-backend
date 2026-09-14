package com.example.HRMS.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the Flyway migration mechanism runs on startup and the database
 * connection is valid: the baseline migration must be applied and reported as
 * successful by Flyway. Uses Flyway's own API so the assertion is
 * database-agnostic (PostgreSQL in real environments, H2 in the test suite).
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationMechanismTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void databaseConnectionIsValid() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    @Test
    void baselineMigrationIsApplied() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();

        // The V1 baseline (from V0-001) must be present and successful.
        boolean baselineApplied = Arrays.stream(applied)
                .anyMatch(info -> "1".equals(info.getVersion().getVersion())
                        && info.getState() == MigrationState.SUCCESS);
        assertThat(baselineApplied)
                .as("V1 baseline migration should be applied successfully")
                .isTrue();

        // Every applied migration must have succeeded (no failed/partial state).
        assertThat(applied)
                .allSatisfy(info ->
                        assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));

        // The current schema version must be the highest applied migration and successful.
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
    }
}
