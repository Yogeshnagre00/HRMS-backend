-- =============================================================================
-- V18 - Payroll result persistence + CALCULATED_PRE_STATUTORY status
--       (HRMS Payroll MVP, task V2-008A non-statutory payroll calculation)
--
-- Introduces the pre-statutory payroll result tables (Data Model 10.2/10.3/10.4)
-- and extends the payroll_run status domain with CALCULATED_PRE_STATUTORY
-- (Data Model 10.1.1, closed in V2-008A.0). This slice persists the NON-statutory
-- calculation result only; it does NOT compute PF/PT/TDS and does NOT create any
-- payroll_statutory_result rows (that table is deferred to the statutory slice).
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * payroll_run.status: the V13 CHECK and VARCHAR(20) do not permit the new
--     value 'CALCULATED_PRE_STATUTORY' (24 chars). V13 is immutable, so this
--     migration drops the old CHECK, widens the column to VARCHAR(30), and adds
--     a new CHECK including the new value. DROP/ADD CONSTRAINT and ALTER COLUMN
--     type are portable to PostgreSQL and H2 (PostgreSQL-compat mode).
--   * payroll_employee_result: statutory + net columns (pf_employee, pf_employer,
--     pt, tds, other_deductions, net_pay) are NULLABLE. NULL = "not yet
--     calculated", explicitly distinct from a computed 0.00 (Data Model 10.2.1).
--     They are never written at the pre-statutory stage.
--   * Money is NUMERIC(18,2); day quantities NUMERIC(8,2)/NUMERIC(4,2) per Data
--     Model. result_status / line_type / leave_treatment / attendance_exception
--     use VARCHAR + CHECK (no native ENUM).
--   * "One result per (payroll_run, employee)" is enforced with a UNIQUE
--     constraint (a plain composite unique, portable to H2; not a partial index).
--     Recalculation replaces the set within one transaction (delete old + insert
--     new), so uniqueness holds.
--   * calculation_explanation / calculation_basis stored as TEXT holding JSON
--     (portable; same approach as import_session validated_data).
-- V1-V17 are not modified.
-- =============================================================================

-- 1. Extend payroll_run status domain -----------------------------------------
ALTER TABLE payroll_run DROP CONSTRAINT ck_payroll_run_status;
ALTER TABLE payroll_run ALTER COLUMN status SET DATA TYPE VARCHAR(30);
ALTER TABLE payroll_run
    ADD CONSTRAINT ck_payroll_run_status
        CHECK (status IN ('DRAFT', 'CALCULATED_PRE_STATUTORY', 'CALCULATED',
                          'HEALTH_CHECK', 'REVIEW', 'APPROVED', 'LOCKED', 'OUTPUTS'));

-- 2. PayrollEmployeeResult (Data Model 10.2) ----------------------------------
CREATE TABLE payroll_employee_result (
    id                        UUID           NOT NULL,
    payroll_run_id            UUID           NOT NULL,
    employee_id               UUID           NOT NULL,
    gross_earnings            NUMERIC(18,2)  NOT NULL,
    pf_employee               NUMERIC(18,2)  NULL,
    pf_employer               NUMERIC(18,2)  NULL,
    pt                        NUMERIC(18,2)  NULL,
    tds                       NUMERIC(18,2)  NULL,
    other_deductions          NUMERIC(18,2)  NULL,
    net_pay                   NUMERIC(18,2)  NULL,
    eligible_calendar_days    NUMERIC(8,2)   NOT NULL,
    lop_days                  NUMERIC(8,2)   NOT NULL,
    payable_calendar_days     NUMERIC(8,2)   NOT NULL,
    manual_tds                BOOLEAN        NOT NULL,
    calculation_explanation   TEXT           NOT NULL,
    result_status             VARCHAR(20)    NOT NULL,
    created_at                TIMESTAMP      NOT NULL,
    CONSTRAINT pk_payroll_employee_result PRIMARY KEY (id),
    CONSTRAINT fk_per_payroll_run
        FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id),
    CONSTRAINT fk_per_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT uq_per_run_employee UNIQUE (payroll_run_id, employee_id),
    CONSTRAINT ck_per_result_status
        CHECK (result_status IN ('VALID', 'REVIEW', 'BLOCKED'))
);

CREATE INDEX ix_per_payroll_run ON payroll_employee_result (payroll_run_id);

-- 3. PayrollResultLine (Data Model 10.3) --------------------------------------
CREATE TABLE payroll_result_line (
    id                            UUID           NOT NULL,
    payroll_employee_result_id    UUID           NOT NULL,
    line_type                     VARCHAR(24)    NOT NULL,
    component_code                VARCHAR(50)    NOT NULL,
    component_name                VARCHAR(255)   NOT NULL,
    amount                        NUMERIC(18,2)  NOT NULL,
    calculation_basis             TEXT           NOT NULL,
    source_record_type            VARCHAR(50)    NULL,
    source_record_id              UUID           NULL,
    CONSTRAINT pk_payroll_result_line PRIMARY KEY (id),
    CONSTRAINT fk_prl_employee_result
        FOREIGN KEY (payroll_employee_result_id)
        REFERENCES payroll_employee_result (id),
    CONSTRAINT ck_prl_line_type
        CHECK (line_type IN ('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION'))
);

CREATE INDEX ix_prl_employee_result
    ON payroll_result_line (payroll_employee_result_id);

-- 4. PayrollDayResult (Data Model 10.4) ---------------------------------------
CREATE TABLE payroll_day_result (
    id                            UUID           NOT NULL,
    payroll_employee_result_id    UUID           NOT NULL,
    work_date                     DATE           NOT NULL,
    employment_eligible           BOOLEAN        NOT NULL,
    scheduled_working_day         BOOLEAN        NOT NULL,
    leave_treatment               VARCHAR(20)    NULL,
    leave_quantity                NUMERIC(4,2)   NULL,
    attendance_exception          VARCHAR(20)    NULL,
    lop_quantity                  NUMERIC(4,2)   NOT NULL,
    compensation_record_id        UUID           NULL,
    payable_day_quantity          NUMERIC(4,2)   NOT NULL,
    CONSTRAINT pk_payroll_day_result PRIMARY KEY (id),
    CONSTRAINT fk_pdr_employee_result
        FOREIGN KEY (payroll_employee_result_id)
        REFERENCES payroll_employee_result (id),
    CONSTRAINT ck_pdr_leave_treatment
        CHECK (leave_treatment IS NULL
               OR leave_treatment IN ('PAID_LEAVE', 'UNPAID_LOP_LEAVE')),
    CONSTRAINT ck_pdr_attendance_exception
        CHECK (attendance_exception IS NULL
               OR attendance_exception IN ('FULL_DAY_ABSENCE', 'HALF_DAY', 'LOP'))
);

CREATE INDEX ix_pdr_employee_result
    ON payroll_day_result (payroll_employee_result_id);
