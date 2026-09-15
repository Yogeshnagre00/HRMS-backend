-- =============================================================================
-- V12 - Compensation record (HRMS Payroll MVP, task V2-006A)
--
-- Introduces the compensation_record table from the approved Data Model
-- specification (section 9.1). Stores effective-dated monthly salary state per
-- employee. This slice stores compensation state only; it does NOT calculate
-- payroll, proration, PF/PT/TDS, variable earnings or arrears.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * All monetary components use NUMERIC(18,2) (fixed decimal, never floating
--     point) exactly as Data Model 9.1 (decimal(18,2)). Non-negative amounts
--     enforced by CHECK constraints at the DB level.
--   * employee_id is the owner FK; created_by references app_user (the actor).
--   * effective_from required; effective_to nullable (null = current/open-ended).
--     "No overlap per employee" (Data Model 17) and the same-day boundary rule
--     are enforced in the service layer (partial/range constraints are not
--     portable to H2 - see AGENTS 8), with tests.
--   * ctc_monthly is an informational monthly total; no cross-component sum
--     constraint is imposed (the documents define no such relationship).
--   * source is a server-controlled string (MANUAL / CSV_IMPORT). reason is an
--     optional admin annotation.
--   * No status, no updated_at, no legal_entity_id (scope derived via employee),
--     no variable-earning/arrear/payroll columns (Data Model 9.1).
-- V1-V11 are not modified.
-- =============================================================================

CREATE TABLE compensation_record (
    id                                UUID           NOT NULL,
    employee_id                       UUID           NOT NULL,
    effective_from                    DATE           NOT NULL,
    effective_to                      DATE           NULL,
    ctc_monthly                       NUMERIC(18,2)  NOT NULL,
    basic_monthly                     NUMERIC(18,2)  NOT NULL,
    hra_monthly                       NUMERIC(18,2)  NOT NULL,
    other_fixed_allowances_monthly    NUMERIC(18,2)  NOT NULL,
    reason                            VARCHAR(255)   NULL,
    source                            VARCHAR(20)    NULL,
    created_by                        UUID           NOT NULL,
    created_at                        TIMESTAMP      NOT NULL,
    CONSTRAINT pk_compensation_record PRIMARY KEY (id),
    CONSTRAINT fk_compensation_record_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_compensation_record_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_compensation_ctc_non_negative
        CHECK (ctc_monthly >= 0),
    CONSTRAINT ck_compensation_basic_non_negative
        CHECK (basic_monthly >= 0),
    CONSTRAINT ck_compensation_hra_non_negative
        CHECK (hra_monthly >= 0),
    CONSTRAINT ck_compensation_other_non_negative
        CHECK (other_fixed_allowances_monthly >= 0)
);

CREATE INDEX ix_compensation_record_employee_id
    ON compensation_record (employee_id, effective_from);
