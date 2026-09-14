package com.example.HRMS.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the V0-002 configuration/master-data schema was created by Flyway with
 * the expected tables, primary keys, foreign keys, NOT NULL and CHECK (enum
 * domain) constraints. Metadata assertions confirm structure; behavioural
 * assertions confirm the constraints are actually enforced by the database.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfigurationSchemaTests {

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

    @Test
    void allExpectedTablesExist() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (String table : EXPECTED_TABLES) {
                assertThat(tableExists(connection, table))
                        .as("table %s should exist", table)
                        .isTrue();
            }
        }
    }

    @Test
    void everyTableHasAPrimaryKey() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : EXPECTED_TABLES) {
                try (ResultSet pk = meta.getPrimaryKeys(null, null, resolveName(connection, table))) {
                    assertThat(pk.next())
                            .as("table %s should have a primary key", table)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void expectedForeignKeysExist() throws Exception {
        assertThat(importedKeyTargets("legal_entity")).contains("company");
        assertThat(importedKeyTargets("statutory_configuration"))
                .contains("legal_entity", "statutory_rule_version_set");
        assertThat(importedKeyTargets("work_calendar")).contains("legal_entity");
        assertThat(importedKeyTargets("work_calendar_assignment")).contains("work_calendar");
    }

    @Test
    void foreignKeyIsEnforced() {
        // legal_entity.company_id must reference an existing company row.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO legal_entity "
                        + "(id, company_id, legal_name, country_code, pan, financial_year_start, "
                        + " status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Orphan Entity', 'IN', 'AAAAA0000A', DATE '2025-04-01', "
                        + " 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notNullConstraintIsEnforced() {
        // company.name is NOT NULL.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO company (id, name, status, created_at, updated_at) "
                        + "VALUES (?, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enumCheckConstraintIsEnforced() {
        // company.status only permits ACTIVE / INACTIVE.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO company (id, name, status, created_at, updated_at) "
                        + "VALUES (?, 'Acme', 'BOGUS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pfApplicabilityRetainsUnconfirmedAsAnExplicitState() {
        // Prove UNCONFIRMED is a storable, first-class value (not silently NO).
        String companyId = UUID.randomUUID().toString();
        String entityId = UUID.randomUUID().toString();
        String ruleSetId = UUID.randomUUID().toString();
        String configId = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "INSERT INTO company (id, name, status, created_at, updated_at) "
                        + "VALUES (?, 'Acme', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                companyId);
        jdbcTemplate.update(
                "INSERT INTO legal_entity "
                        + "(id, company_id, legal_name, country_code, pan, financial_year_start, "
                        + " status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Acme India', 'IN', 'AAAAA0000A', DATE '2025-04-01', "
                        + " 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                entityId, companyId);
        jdbcTemplate.update(
                "INSERT INTO statutory_rule_version_set "
                        + "(id, jurisdiction, pf_rule_version, pt_rule_version, tds_rule_version, "
                        + " effective_from, source_reference, verified_at, status) "
                        + "VALUES (?, 'IN', 'PF-2025', 'PT-2025', 'TDS-2025', DATE '2025-04-01', "
                        + " 'ref', CURRENT_TIMESTAMP, 'VERIFIED')",
                ruleSetId);
        jdbcTemplate.update(
                "INSERT INTO statutory_configuration "
                        + "(id, legal_entity_id, effective_from, pf_applicability, tds_policy, "
                        + " rule_version_set_id, created_by, created_at) "
                        + "VALUES (?, ?, DATE '2025-04-01', 'UNCONFIRMED', 'NEW_REGIME_AUTOMATIC_V0', "
                        + " ?, ?, CURRENT_TIMESTAMP)",
                configId, entityId, ruleSetId, UUID.randomUUID().toString());

        String stored = jdbcTemplate.queryForObject(
                "SELECT pf_applicability FROM statutory_configuration WHERE id = ?",
                String.class, configId);
        assertThat(stored).isEqualTo("UNCONFIRMED");
    }

    // --- helpers -----------------------------------------------------------

    private boolean tableExists(Connection connection, String table) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, resolveName(connection, table),
                new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private List<String> importedKeyTargets(String table) throws Exception {
        List<String> targets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getImportedKeys(null, null, resolveName(connection, table))) {
                while (rs.next()) {
                    targets.add(rs.getString("PKTABLE_NAME").toLowerCase());
                }
            }
        }
        return targets;
    }

    /**
     * H2 stores unquoted identifiers in upper case while PostgreSQL uses lower
     * case. Resolve the stored form so metadata lookups work on both databases.
     */
    private String resolveName(Connection connection, String lowerName) throws Exception {
        return connection.getMetaData().storesUpperCaseIdentifiers()
                ? lowerName.toUpperCase()
                : lowerName;
    }
}
