-- =============================================================================
-- V17 - Variable earning + arrear (HRMS Payroll MVP, task V2-008A.6)
--
-- Introduces the variable_earning and arrear tables from the approved Data
-- Model specification (sections 9.2 and 9.3). Both are independent
-- payroll-period INPUTS, each attached to exactly one PayrollRun. This slice
-- stores inputs only; it performs no payroll calculation, no proration and no
-- statutory treatment, and never modifies compensation_record.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * amount is NUMERIC(18,2) (fixed decimal, never floating point), non-negative
--     enforced by a CHECK constraint (negative is rejected; no silent deduction).
--   * employee_id, payroll_run_id and created_by are FKs (employee, payroll_run
--     and app_user all exist).
--   * DRAFT-only creation (V2-008A.6.1) is a PayrollRun-status precondition
--     enforced in the SERVICE layer, not the schema.
--   * No status/effective-date/approval columns (Data Model 9.2/9.3 define
--     none). Multiple rows per (employee, payroll_run) are valid — no uniqueness
--     constraint. period_reference/reason are required non-blank strings for
--     arrear (enforced in the service/DTO); description required for variable
--     earning. source is an optional variable-earning annotation.
--   * Composite indexes on (employee_id, payroll_run_id) support the collection
--     queries and FK lookups.
-- V1-V16 are not modified.
-- =============================================================================

CREATE TABLE variable_earning (
    id               UUID           NOT NULL,
    employee_id      UUID           NOT NULL,
    payroll_run_id   UUID           NOT NULL,
    description      VARCHAR(255)   NOT NULL,
    amount           NUMERIC(18,2)  NOT NULL,
    source           VARCHAR(255)   NULL,
    created_by       UUID           NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    CONSTRAINT pk_variable_earning PRIMARY KEY (id),
    CONSTRAINT fk_variable_earning_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_variable_earning_payroll_run
        FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id),
    CONSTRAINT fk_variable_earning_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_variable_earning_amount_non_negative
        CHECK (amount >= 0)
);

CREATE INDEX ix_variable_earning_employee_run
    ON variable_earning (employee_id, payroll_run_id);

CREATE TABLE arrear (
    id                 UUID           NOT NULL,
    employee_id        UUID           NOT NULL,
    payroll_run_id     UUID           NOT NULL,
    amount             NUMERIC(18,2)  NOT NULL,
    period_reference   VARCHAR(255)   NOT NULL,
    reason             VARCHAR(255)   NOT NULL,
    created_by         UUID           NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    CONSTRAINT pk_arrear PRIMARY KEY (id),
    CONSTRAINT fk_arrear_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_arrear_payroll_run
        FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id),
    CONSTRAINT fk_arrear_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_arrear_amount_non_negative
        CHECK (amount >= 0)
);

CREATE INDEX ix_arrear_employee_run
    ON arrear (employee_id, payroll_run_id);
