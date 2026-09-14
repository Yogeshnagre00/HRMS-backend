-- =============================================================================
-- V8 - Employee bank account (HRMS Payroll MVP, task V2-002)
--
-- Introduces the employee_bank_account table from the approved Data Model
-- specification (section 7.3). Bank details are used later to produce the
-- generic bank-transfer CSV; payment execution/integration is out of v0.
--
-- Design notes (consistent with prior slices and AGENTS.md §8):
--   * Enum domains are VARCHAR + CHECK (portable across PostgreSQL and the H2
--     PostgreSQL-mode test database). status has a fixed authoritative value
--     set (ACTIVE/INACTIVE) and is constrained.
--   * employee_id is the owner FK (bank account belongs to an Employee).
--   * account_number is a plain string column exactly as the Data Model types
--     it. The Data Model note "Secure storage/masked UI" states an intent but
--     the authoritative v0.1 documents do not specify an encryption-at-rest or
--     masking mechanism/algorithm/key management. Per task governance, no such
--     mechanism is invented here; the unspecified storage-security treatment is
--     reported as an open product/security decision. The number must remain
--     retrievable in cleartext for the downstream bank-transfer CSV export.
--   * "One active primary" and "one current (open) bank account per employee"
--     are conditional invariants enforced in the service layer (partial/filtered
--     unique indexes are not portable to H2 — see AGENTS.md §8), with tests.
--   * Fields follow Data Model 7.3 exactly. No created_at/updated_at/created_by
--     columns are added because the authoritative model does not define them for
--     this entity; audit is recorded via the append-only audit_log.
--   * No future-domain tables (opening tax state, opening leave, CSV import,
--     payroll) are created here.
-- V1-V7 are not modified.
-- =============================================================================

CREATE TABLE employee_bank_account (
    id                   UUID         NOT NULL,
    employee_id          UUID         NOT NULL,
    account_number       VARCHAR(34)  NOT NULL,
    ifsc                 VARCHAR(20)  NOT NULL,
    account_holder_name  VARCHAR(255) NULL,
    is_primary           BOOLEAN      NOT NULL,
    effective_from       DATE         NOT NULL,
    effective_to         DATE         NULL,
    status               VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_employee_bank_account PRIMARY KEY (id),
    CONSTRAINT fk_employee_bank_account_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT ck_employee_bank_account_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_employee_bank_account_employee_id
    ON employee_bank_account (employee_id);
