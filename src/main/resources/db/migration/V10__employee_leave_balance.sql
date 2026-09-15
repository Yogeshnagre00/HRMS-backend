-- =============================================================================
-- V10 - Employee opening paid-leave balance (HRMS Payroll MVP, task V2-004)
--
-- Introduces the employee_leave_balance table from the approved Data Model
-- specification (section 8.2). Stores the employee's current-financial-year
-- opening/current paid-leave balance captured during onboarding. This slice
-- stores/retrieves the balance only; it does NOT implement leave transactions,
-- LOP, carry-forward, or payroll calculation.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * leave_treatment is VARCHAR + CHECK (PAID_LEAVE) - v0 supports only the
--     balance-managed Paid Leave treatment (Data Model 8.2). Portable across
--     PostgreSQL and the H2 PostgreSQL-mode test database.
--   * employee_id is the owner FK (balance belongs to an Employee).
--   * financial_year is the canonical YYYY-YY key (Data Model 17.1), server-
--     derived and never client-supplied for this current-FY resource.
--   * opening_balance / approved_additions / used_quantity / available_balance
--     use NUMERIC(8,2) (fixed decimal, never floating point) EXACTLY as the
--     Data Model 8.2 types them (decimal(8,2)). These are day quantities, not
--     rupee amounts, so the authoritative scale is (8,2), not (18,2).
--   * available_balance is derived and persisted:
--       available_balance = opening_balance + approved_additions - used_quantity
--     Business Rules 24.5 / 6.4: an insufficient (would-be-negative) balance is
--     an explicitly-surfaced state downstream; it is NOT capped to zero and NOT
--     converted to LOP here, so no non-negative CHECK is placed on
--     available_balance.
--   * Unique (employee_id, financial_year) enforces the authoritative invariant
--     (Data Model 8.2 / 17). The DB constraint is authoritative and protects
--     against concurrent races.
--   * updated_at per Data Model 8.2. No future-domain tables are created here.
-- V1-V9 are not modified.
-- =============================================================================

CREATE TABLE employee_leave_balance (
    id                   UUID          NOT NULL,
    employee_id          UUID          NOT NULL,
    leave_treatment      VARCHAR(20)   NOT NULL,
    financial_year       VARCHAR(7)    NOT NULL,
    opening_balance      NUMERIC(8,2)  NOT NULL,
    approved_additions   NUMERIC(8,2)  NOT NULL,
    used_quantity        NUMERIC(8,2)  NOT NULL,
    available_balance    NUMERIC(8,2)  NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    CONSTRAINT pk_employee_leave_balance PRIMARY KEY (id),
    CONSTRAINT fk_employee_leave_balance_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT uq_employee_leave_balance_employee_fy
        UNIQUE (employee_id, financial_year),
    CONSTRAINT ck_employee_leave_balance_treatment
        CHECK (leave_treatment IN ('PAID_LEAVE'))
);

CREATE INDEX ix_employee_leave_balance_employee_id
    ON employee_leave_balance (employee_id);
