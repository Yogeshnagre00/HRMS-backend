-- =============================================================================
-- V11 - Employee CSV import session (HRMS Payroll MVP, task V2-005)
--
-- Introduces import_session and import_session_row from the approved Data Model
-- specification (sections 7.4 and 7.5). These back the CSV upload+validation
-- flow (API §11). They are VALIDATION-SESSION persistence only — they are NOT
-- employees, bank accounts, tax state, leave balances, or payroll records, and
-- creating them never creates or modifies employee business data.
--
-- Design notes (consistent with prior slices and AGENTS.md §8):
--   * status is VARCHAR + CHECK (VALIDATED/CONFIRMED). V2-005 only creates
--     VALIDATED sessions; CONFIRMED is set later by V2-006.
--   * company_id / legal_entity_id are scope-owner FKs; the session is company/
--     legal-entity scoped and retrieval enforces the same isolation.
--   * Row counts are integers. Timestamps use TIMESTAMP; confirmed_at nullable.
--   * validated_data and issues are stored as TEXT holding JSON (Data Model 7.5
--     "json/text"). Portable TEXT is used rather than native jsonb so the same
--     migration runs on PostgreSQL and the H2 PostgreSQL-mode test database.
--     The raw uploaded CSV file is NOT stored; only structured validated state.
--   * import_session_row is ordered by row_number for deterministic retrieval;
--     an index on import_session_id supports session lookups.
-- V1-V10 are not modified.
-- =============================================================================

CREATE TABLE import_session (
    id                  UUID          NOT NULL,
    company_id          UUID          NOT NULL,
    legal_entity_id     UUID          NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    original_file_name  VARCHAR(255)  NULL,
    total_rows          INTEGER       NOT NULL,
    valid_rows          INTEGER       NOT NULL,
    invalid_rows        INTEGER       NOT NULL,
    warning_rows        INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL,
    validated_at        TIMESTAMP     NOT NULL,
    confirmed_at        TIMESTAMP     NULL,
    CONSTRAINT pk_import_session PRIMARY KEY (id),
    CONSTRAINT fk_import_session_company
        FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_import_session_legal_entity
        FOREIGN KEY (legal_entity_id) REFERENCES legal_entity (id),
    CONSTRAINT ck_import_session_status
        CHECK (status IN ('VALIDATED', 'CONFIRMED'))
);

CREATE INDEX ix_import_session_company_id ON import_session (company_id);

CREATE TABLE import_session_row (
    id                 UUID     NOT NULL,
    import_session_id  UUID     NOT NULL,
    row_number         INTEGER  NOT NULL,
    is_valid           BOOLEAN  NOT NULL,
    validated_data     TEXT     NOT NULL,
    issues             TEXT     NOT NULL,
    CONSTRAINT pk_import_session_row PRIMARY KEY (id),
    CONSTRAINT fk_import_session_row_session
        FOREIGN KEY (import_session_id) REFERENCES import_session (id)
);

CREATE INDEX ix_import_session_row_session
    ON import_session_row (import_session_id, row_number);
