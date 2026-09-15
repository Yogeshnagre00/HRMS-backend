-- =============================================================================
-- V16 - Leave entry (HRMS Payroll MVP, task V2-008A.5)
--
-- Introduces the leave_entry table from the approved Data Model specification
-- (section 8.1). A LeaveEntry is a date-specific, administrator-entered leave
-- record (distinct from the employee_leave_balance state in V10). PAID_LEAVE
-- entries consume the employee's current-FY paid-leave balance; UNPAID_LOP_LEAVE
-- entries record explicit LOP and never touch the balance. This slice stores
-- leave INPUT + owns paid-leave balance consumption (V2-008A.2); it performs no
-- payroll calculation.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * treatment is VARCHAR + CHECK (PAID_LEAVE/UNPAID_LOP_LEAVE); status is
--     VARCHAR + CHECK (RECORDED/CANCELLED). Native ENUM types are not used
--     (H2 compatibility).
--   * quantity is NUMERIC(4,2) per Data Model 8.1 (v0 values 0.5 or 1.0;
--     enforced in the service layer).
--   * employee_id and created_by are FKs (employee and app_user both exist).
--   * "One active (RECORDED) entry per employee per leave_date" is a conditional
--     invariant enforced in the SERVICE layer (portable to H2; no partial unique
--     index). A composite index on (employee_id, leave_date) supports the
--     duplicate check and list/lookup queries.
--   * No approval/source/updated_at columns (Data Model 8.1 defines none); no
--     payroll_run FK (leave is a source input, not a payroll result).
-- V1-V15 are not modified.
-- =============================================================================

CREATE TABLE leave_entry (
    id            UUID          NOT NULL,
    employee_id   UUID          NOT NULL,
    leave_date    DATE          NOT NULL,
    treatment     VARCHAR(20)   NOT NULL,
    quantity      NUMERIC(4,2)  NOT NULL,
    reason        VARCHAR(255)  NULL,
    status        VARCHAR(20)   NOT NULL,
    created_by    UUID          NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    CONSTRAINT pk_leave_entry PRIMARY KEY (id),
    CONSTRAINT fk_leave_entry_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_leave_entry_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_leave_entry_treatment
        CHECK (treatment IN ('PAID_LEAVE', 'UNPAID_LOP_LEAVE')),
    CONSTRAINT ck_leave_entry_status
        CHECK (status IN ('RECORDED', 'CANCELLED')),
    CONSTRAINT ck_leave_entry_quantity_positive
        CHECK (quantity > 0)
);

CREATE INDEX ix_leave_entry_employee_date
    ON leave_entry (employee_id, leave_date);
