-- =============================================================================
-- V15 - Attendance exception (HRMS Payroll MVP, task V2-008A.4)
--
-- Introduces the attendance_exception table from the approved Data Model
-- specification (section 8.3). v0 attendance is exception-based and
-- administrator-entered: a scheduled working day is Present unless an exception
-- exists, so only exceptions are persisted (never a row per Present day). This
-- slice stores attendance-exception INPUT only; it does NOT calculate payable
-- days, LOP money, or any payroll result.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * exception_type is VARCHAR + CHECK (FULL_DAY_ABSENCE/HALF_DAY/LOP); native
--     ENUM types are not used (H2 compatibility).
--   * quantity is NUMERIC(4,2) per Data Model 8.3 (v0 values 0.5 or 1.0;
--     type<->quantity consistency is enforced in the service layer).
--   * employee_id and created_by are FKs (employee and app_user both exist).
--   * "One exception per employee per attendance_date" is a conditional
--     invariant enforced in the SERVICE layer (portable to H2; no partial
--     unique index). A composite index on (employee_id, attendance_date)
--     supports the duplicate check and list/lookup queries.
--   * No status/source/approved_by/check_in/check_out/shift columns (Data Model
--     8.3 defines none); no payroll_run FK (attendance is a source input, not a
--     payroll result).
-- V1-V14 are not modified.
-- =============================================================================

CREATE TABLE attendance_exception (
    id                UUID          NOT NULL,
    employee_id       UUID          NOT NULL,
    attendance_date   DATE          NOT NULL,
    exception_type    VARCHAR(20)   NOT NULL,
    quantity          NUMERIC(4,2)  NOT NULL,
    reason            VARCHAR(255)  NULL,
    created_by        UUID          NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    CONSTRAINT pk_attendance_exception PRIMARY KEY (id),
    CONSTRAINT fk_attendance_exception_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_attendance_exception_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_attendance_exception_type
        CHECK (exception_type IN ('FULL_DAY_ABSENCE', 'HALF_DAY', 'LOP')),
    CONSTRAINT ck_attendance_exception_quantity_positive
        CHECK (quantity > 0)
);

CREATE INDEX ix_attendance_exception_employee_date
    ON attendance_exception (employee_id, attendance_date);
