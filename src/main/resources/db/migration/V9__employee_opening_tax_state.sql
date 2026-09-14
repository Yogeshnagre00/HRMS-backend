-- =============================================================================
-- V9 - Employee opening FY tax state (HRMS Payroll MVP, task V2-003)
--
-- Introduces the employee_opening_tax_state table from the approved Data Model
-- specification (section 7.2). Stores the employee's current-financial-year
-- opening state (cumulative taxable income and TDS already deducted) that later
-- automatic New-Regime TDS depends on. This slice only persists/retrieves the
-- state; it does NOT calculate TDS, taxable income, or annual tax.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * source is VARCHAR + CHECK (CSV_IMPORT / MANUAL) - portable across
--     PostgreSQL and the H2 PostgreSQL-mode test database.
--   * employee_id is the owner FK (opening state belongs to an Employee).
--   * financial_year is the canonical YYYY-YY key (Data Model 17.1); the value
--     is server-derived and never client-supplied for this current-FY resource.
--   * Monetary columns use NUMERIC(18,2) (fixed decimal, never floating point),
--     matching Data Model 7.2 decimal(18,2+).
--   * Unique (employee_id, financial_year) enforces the authoritative invariant
--     (Data Model 7.2 / 17): at most one opening tax state per employee per FY.
--     The DB constraint is authoritative and protects against concurrent races.
--   * created_at only; the Data Model does not define updated_at/created_by for
--     this entity, so neither is added. Audit is via the append-only audit_log.
--   * No future-domain tables are created here. V1-V8 are not modified.
-- =============================================================================

CREATE TABLE employee_opening_tax_state (
    id                         UUID           NOT NULL,
    employee_id                UUID           NOT NULL,
    financial_year             VARCHAR(7)     NOT NULL,
    cumulative_taxable_income  NUMERIC(18,2)  NOT NULL,
    tds_already_deducted       NUMERIC(18,2)  NOT NULL,
    source                     VARCHAR(20)    NOT NULL,
    created_at                 TIMESTAMP      NOT NULL,
    CONSTRAINT pk_employee_opening_tax_state PRIMARY KEY (id),
    CONSTRAINT fk_eots_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT uq_eots_employee_financial_year
        UNIQUE (employee_id, financial_year),
    CONSTRAINT ck_eots_source
        CHECK (source IN ('CSV_IMPORT', 'MANUAL'))
);

CREATE INDEX ix_eots_employee_id ON employee_opening_tax_state (employee_id);
