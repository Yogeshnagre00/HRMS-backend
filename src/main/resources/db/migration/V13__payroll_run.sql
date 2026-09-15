-- =============================================================================
-- V13 - Payroll run (HRMS Payroll MVP, task V2-007 Payroll Run Foundation)
--
-- Introduces the payroll_run table from the approved Data Model specification
-- (section 10.1). This is the PAYROLL RUN FOUNDATION only: it establishes the
-- monthly payroll run, its lifecycle status, legal-entity ownership, the
-- referenced statutory rule-version set, correction lineage self-reference and
-- lifecycle metadata columns. It does NOT calculate payroll and does NOT create
-- any employee-result, day-result, statutory-result, health-check, approval,
-- lock, correction or output tables (those are later slices).
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * status is VARCHAR + CHECK over the authoritative lifecycle values
--     (DRAFT/CALCULATED/HEALTH_CHECK/REVIEW/APPROVED/LOCKED/OUTPUTS). Native
--     ENUM types are not used (H2 compatibility). V2-007 creates only DRAFT
--     runs; later slices drive the transitions.
--   * payroll_month is a DATE holding the canonical first day of the payroll
--     month (Data Model 10.1). Monthly-only; first-of-month is enforced in the
--     service layer with a 400 on non-first-day input.
--   * financial_year is the server-derived canonical YYYY-YY key (Data Model
--     17.1), derived from the payroll month and the legal entity's
--     financial_year_start via the shared FinancialYearResolver.
--   * rule_version_set_id is REQUIRED and FKs the verified statutory rule set;
--     no placeholder/fake rule-version row is created here.
--   * created_by / approved_by / locked_by FK app_user. approved_*, locked_*,
--     calculated_at and parent_payroll_run_id are nullable lifecycle metadata
--     populated by later slices; V2-007 leaves them null.
--   * parent_payroll_run_id is a nullable self-FK reserved for correction-run
--     lineage (later slice). Normal V2-007 runs are primary (parent = null).
--   * "Primary run unique by (legal_entity_id, payroll_month)" (Data Model 10.1,
--     17) is a CONDITIONAL invariant (only where parent_payroll_run_id IS NULL),
--     so it is enforced in the SERVICE layer, not via a partial/filtered unique
--     index (not portable to H2 - AGENTS.md 8). A non-unique lookup index on
--     (legal_entity_id, payroll_month) supports that check and list queries.
-- V1-V12 are not modified.
-- =============================================================================

CREATE TABLE payroll_run (
    id                       UUID          NOT NULL,
    legal_entity_id          UUID          NOT NULL,
    payroll_month            DATE          NOT NULL,
    financial_year           VARCHAR(7)    NOT NULL,
    status                   VARCHAR(20)   NOT NULL,
    calculation_version      VARCHAR(50)   NOT NULL,
    rule_version_set_id      UUID          NOT NULL,
    calculated_at            TIMESTAMP     NULL,
    approved_at              TIMESTAMP     NULL,
    approved_by              UUID          NULL,
    locked_at                TIMESTAMP     NULL,
    locked_by                UUID          NULL,
    parent_payroll_run_id    UUID          NULL,
    created_by               UUID          NOT NULL,
    created_at               TIMESTAMP     NOT NULL,
    CONSTRAINT pk_payroll_run PRIMARY KEY (id),
    CONSTRAINT fk_payroll_run_legal_entity
        FOREIGN KEY (legal_entity_id) REFERENCES legal_entity (id),
    CONSTRAINT fk_payroll_run_rule_version_set
        FOREIGN KEY (rule_version_set_id) REFERENCES statutory_rule_version_set (id),
    CONSTRAINT fk_payroll_run_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_payroll_run_approved_by
        FOREIGN KEY (approved_by) REFERENCES app_user (id),
    CONSTRAINT fk_payroll_run_locked_by
        FOREIGN KEY (locked_by) REFERENCES app_user (id),
    CONSTRAINT fk_payroll_run_parent
        FOREIGN KEY (parent_payroll_run_id) REFERENCES payroll_run (id),
    CONSTRAINT ck_payroll_run_status
        CHECK (status IN ('DRAFT', 'CALCULATED', 'HEALTH_CHECK', 'REVIEW',
                          'APPROVED', 'LOCKED', 'OUTPUTS'))
);

CREATE INDEX ix_payroll_run_entity_month
    ON payroll_run (legal_entity_id, payroll_month);
