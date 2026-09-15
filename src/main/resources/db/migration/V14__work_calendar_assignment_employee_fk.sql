-- =============================================================================
-- V14 - Finalize work_calendar_assignment.employee_id FK (task V2-008A.3)
--
-- The work_calendar and work_calendar_assignment tables were created in V2
-- (Data Model 6.1/6.2). At that time the Employee table did not exist, so
-- work_calendar_assignment.employee_id was created as a typed UUID NOT NULL
-- column WITHOUT its foreign key (documented deferral, same pattern as the
-- created_by FKs finalized in V3). The Employee table now exists (V7), so this
-- migration adds the deferred foreign key. This is the only schema change in
-- the Work Calendar + Assignment slice; the tables themselves are reused.
--
-- Design notes (consistent with prior slices and AGENTS.md 8):
--   * ALTER TABLE ADD CONSTRAINT ... FOREIGN KEY is portable to PostgreSQL and
--     H2 (PostgreSQL-compat mode); the same idiom is used in V3.
--   * No columns are added/changed; no other table is touched.
--   * Calendar effective-date no-overlap and assignment no-overlap invariants
--     are conditional/range rules enforced in the service layer (not via
--     partial/filtered unique indexes, which are not portable to H2).
-- V1-V13 are not modified.
-- =============================================================================

ALTER TABLE work_calendar_assignment
    ADD CONSTRAINT fk_wca_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id);
