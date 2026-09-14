-- =============================================================================
-- V7 - Employee master (HRMS Payroll MVP, task V2-001)
--
-- Introduces the employee table from the approved Data Model specification
-- (section 7.1). This is the table whose creation was explicitly DEFERRED by
-- V0-002 (see V2 note: "employee_id references the future employee table
-- (V2-001, out of scope here)").
--
-- Design notes (consistent with V0-002 decisions and AGENTS.md §8):
--   * Enum domains are VARCHAR + CHECK (portable across PostgreSQL and the H2
--     PostgreSQL-mode test database). tax_regime and status have a fixed,
--     authoritative value set and are constrained. employment_type is modelled
--     as VARCHAR WITHOUT a CHECK: the Data Model types it "string/enum" as a
--     "v0-supported type" but does not enumerate an authoritative fixed set, so
--     a hard CHECK would risk rejecting legitimate values; the supported-type
--     rule is validated in the service layer instead (non-blank).
--   * employee_id is the business identifier and is UNIQUE WITHIN THE LEGAL
--     ENTITY (Data Model 7.1), enforced by a composite UNIQUE constraint. It is
--     distinct from the surrogate UUID primary key.
--   * legal_entity_id is the owner FK (Employee belongs to LegalEntity).
--   * Dates use DATE; audit timestamps use TIMESTAMP. No monetary columns here.
--   * This migration deliberately does NOT add the deferred
--     work_calendar_assignment.employee_id foreign key: that belongs to the
--     Work Calendar slice, not Employee CRUD, and adding it here would exceed
--     V2-001 scope. V1-V6 are not modified.
--   * No future-domain tables (bank, opening tax state, opening leave) are
--     created here; they are separate later slices.
-- =============================================================================

CREATE TABLE employee (
    id                UUID         NOT NULL,
    legal_entity_id   UUID         NOT NULL,
    employee_id       VARCHAR(50)  NOT NULL,
    full_name         VARCHAR(255) NOT NULL,
    joining_date      DATE         NOT NULL,
    exit_date         DATE         NULL,
    employment_type   VARCHAR(50)  NOT NULL,
    department        VARCHAR(255) NULL,
    designation       VARCHAR(255) NULL,
    location          VARCHAR(255) NULL,
    pan               VARCHAR(10)  NOT NULL,
    uan               VARCHAR(20)  NULL,
    pt_state          VARCHAR(100) NULL,
    tax_regime        VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT pk_employee PRIMARY KEY (id),
    CONSTRAINT fk_employee_legal_entity
        FOREIGN KEY (legal_entity_id) REFERENCES legal_entity (id),
    CONSTRAINT uq_employee_entity_business_id
        UNIQUE (legal_entity_id, employee_id),
    CONSTRAINT ck_employee_tax_regime
        CHECK (tax_regime IN ('NEW_REGIME', 'OLD_REGIME')),
    CONSTRAINT ck_employee_status
        CHECK (status IN ('ACTIVE', 'EXITED', 'INACTIVE'))
);

CREATE INDEX ix_employee_legal_entity_id ON employee (legal_entity_id);
