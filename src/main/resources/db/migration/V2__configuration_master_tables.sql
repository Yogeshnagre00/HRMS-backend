-- =============================================================================
-- V2 - Configuration / master-data baseline (HRMS Payroll MVP, task V0-002)
--
-- First real HRMS schema. Implements the foundational configuration/master-data
-- entities from the approved Data Model specification (sections 4, 5, 6) in
-- strict dependency order:
--   1. company
--   2. legal_entity                 (FK -> company)
--   3. statutory_rule_version_set   (referenced by statutory_configuration)
--   4. statutory_configuration      (FK -> legal_entity, statutory_rule_version_set)
--   5. work_calendar                (FK -> legal_entity)
--   6. work_calendar_assignment     (FK -> work_calendar; employee FK deferred)
--
-- Design notes (see task decision log):
--   * Enum domains are modelled as VARCHAR + CHECK constraints. This is portable
--     across PostgreSQL (the production target) and the H2 PostgreSQL-mode test
--     database. Native PostgreSQL ENUM types are intentionally avoided because
--     they do not run on H2 and would break the migration test suite.
--   * Monetary values are not present in these six tables. Where they appear in
--     later entities they will use NUMERIC (fixed decimal), never floating point.
--   * Timestamps use TIMESTAMP; effective-dated boundaries use DATE.
--   * Columns that reference tables not yet created in this task
--     (created_by -> future users/actor table; work_calendar_assignment.employee_id
--     -> future employee table) are typed as UUID NOT NULL WITHOUT a FK constraint.
--     The FK is added by the migration that introduces the target table. Adding a
--     speculative users/employee table now is explicitly out of scope for V0-002.
--   * Conditional "exactly one ACTIVE per company/entity" and "no overlapping
--     effective ranges" invariants are enforced in the application/service layer
--     in later tasks. They require partial/range constraints that are not portable
--     to H2, and the Data Model (section 23) treats index strategy as an
--     implementation decision that must not alter the business invariants.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. company  (Data Model 4.1)
-- ---------------------------------------------------------------------------
CREATE TABLE company (
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_company PRIMARY KEY (id),
    CONSTRAINT ck_company_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ---------------------------------------------------------------------------
-- 2. legal_entity  (Data Model 4.2)
-- ---------------------------------------------------------------------------
CREATE TABLE legal_entity (
    id                    UUID         NOT NULL,
    company_id            UUID         NOT NULL,
    legal_name            VARCHAR(255) NOT NULL,
    country_code          VARCHAR(2)   NOT NULL,
    pan                   VARCHAR(10)  NOT NULL,
    financial_year_start  DATE         NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT pk_legal_entity PRIMARY KEY (id),
    CONSTRAINT fk_legal_entity_company
        FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT ck_legal_entity_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_legal_entity_company_id ON legal_entity (company_id);

-- ---------------------------------------------------------------------------
-- 3. statutory_rule_version_set  (Data Model 5.2)
--    Created before statutory_configuration because the latter references it.
-- ---------------------------------------------------------------------------
CREATE TABLE statutory_rule_version_set (
    id                UUID         NOT NULL,
    jurisdiction      VARCHAR(100) NOT NULL,
    pf_rule_version   VARCHAR(100) NOT NULL,
    pt_rule_version   VARCHAR(100) NOT NULL,
    tds_rule_version  VARCHAR(100) NOT NULL,
    effective_from    DATE         NOT NULL,
    effective_to      DATE         NULL,
    source_reference  VARCHAR(500) NOT NULL,
    verified_at       TIMESTAMP    NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_statutory_rule_version_set PRIMARY KEY (id),
    CONSTRAINT ck_srvs_status
        CHECK (status IN ('VERIFIED', 'SUPERSEDED', 'DRAFT'))
);

CREATE INDEX ix_srvs_effective_from ON statutory_rule_version_set (effective_from);

-- ---------------------------------------------------------------------------
-- 4. statutory_configuration  (Data Model 5.1)
--    pf_applicability is explicit and includes UNCONFIRMED; UNCONFIRMED is a
--    stored state and must never be silently treated as NO (enforced by the
--    CHECK domain plus later Health Check business rules).
-- ---------------------------------------------------------------------------
CREATE TABLE statutory_configuration (
    id                      UUID         NOT NULL,
    legal_entity_id         UUID         NOT NULL,
    effective_from          DATE         NOT NULL,
    effective_to            DATE         NULL,
    pf_applicability        VARCHAR(20)  NOT NULL,
    pf_registration_status  VARCHAR(30)  NULL,
    pf_registration_number  VARCHAR(50)  NULL,
    pt_state                VARCHAR(100) NULL,
    tds_policy              VARCHAR(30)  NOT NULL,
    rule_version_set_id     UUID         NOT NULL,
    -- created_by references the future users/actor table (Auth/RBAC task).
    -- Typed and NOT NULL per the Data Model; FK added when that table exists.
    created_by              UUID         NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    CONSTRAINT pk_statutory_configuration PRIMARY KEY (id),
    CONSTRAINT fk_statutory_config_legal_entity
        FOREIGN KEY (legal_entity_id) REFERENCES legal_entity (id),
    CONSTRAINT fk_statutory_config_rule_version_set
        FOREIGN KEY (rule_version_set_id) REFERENCES statutory_rule_version_set (id),
    CONSTRAINT ck_statutory_config_pf_applicability
        CHECK (pf_applicability IN ('YES', 'NO', 'UNCONFIRMED')),
    CONSTRAINT ck_statutory_config_pf_registration_status
        CHECK (pf_registration_status IS NULL
               OR pf_registration_status IN ('REGISTERED', 'NOT_REGISTERED', 'VOLUNTARY_COVERAGE')),
    CONSTRAINT ck_statutory_config_tds_policy
        CHECK (tds_policy IN ('NEW_REGIME_AUTOMATIC_V0'))
);

CREATE INDEX ix_statutory_config_legal_entity_id
    ON statutory_configuration (legal_entity_id);
CREATE INDEX ix_statutory_config_rule_version_set_id
    ON statutory_configuration (rule_version_set_id);
CREATE INDEX ix_statutory_config_effective_from
    ON statutory_configuration (legal_entity_id, effective_from);

-- ---------------------------------------------------------------------------
-- 5. work_calendar  (Data Model 6.1)
--    Kept as a separate entity; employees reference it via assignment (later)
--    rather than embedding a hardcoded 5-day flag in the employee record.
-- ---------------------------------------------------------------------------
CREATE TABLE work_calendar (
    id                          UUID         NOT NULL,
    legal_entity_id             UUID         NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    effective_from              DATE         NOT NULL,
    effective_to                DATE         NULL,
    monday_to_friday            BOOLEAN      NOT NULL,
    saturday_sunday_weekly_off  BOOLEAN      NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_work_calendar PRIMARY KEY (id),
    CONSTRAINT fk_work_calendar_legal_entity
        FOREIGN KEY (legal_entity_id) REFERENCES legal_entity (id),
    CONSTRAINT ck_work_calendar_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_work_calendar_legal_entity_id ON work_calendar (legal_entity_id);

-- ---------------------------------------------------------------------------
-- 6. work_calendar_assignment  (Data Model 6.2)
--    employee_id references the future employee table (V2-001, out of scope
--    here). Typed and NOT NULL per the Data Model; FK added when that table
--    exists. work_calendar_id FK is created now because work_calendar exists.
-- ---------------------------------------------------------------------------
CREATE TABLE work_calendar_assignment (
    id                UUID       NOT NULL,
    employee_id       UUID       NOT NULL,
    work_calendar_id  UUID       NOT NULL,
    effective_from    DATE       NOT NULL,
    effective_to      DATE       NULL,
    created_by        UUID       NOT NULL,
    CONSTRAINT pk_work_calendar_assignment PRIMARY KEY (id),
    CONSTRAINT fk_wca_work_calendar
        FOREIGN KEY (work_calendar_id) REFERENCES work_calendar (id)
);

CREATE INDEX ix_wca_employee_id ON work_calendar_assignment (employee_id);
CREATE INDEX ix_wca_work_calendar_id ON work_calendar_assignment (work_calendar_id);
