-- =============================================================================
-- V19 - Versioned statutory rule-value store (PF / PT / TDS foundation)
--       (HRMS Payroll MVP, task Phase 2)
--
-- Purpose: make it possible for a PayrollRun -> StatutoryRuleVersionSet to carry
-- an immutable set of PF/PT/TDS statutory RULE VALUES, WITHOUT hardcoding any
-- legal constant in application code and WITHOUT inventing statutory values.
--
-- This migration creates the STRUCTURE only. It inserts NO statutory values
-- (no rates, ceilings, slabs). The three child tables hang off the existing
-- V0-002 statutory_rule_version_set(id) (immutable header) so existing V2-007
-- PayrollRun binding is preserved unchanged.
--
-- Design (consistent with AGENTS.md 8 and prior slices):
--   * Hybrid representation: normalized, queryable/auditable provenance +
--     dimension columns (rule_type, jurisdiction, pt_state, local_body,
--     periodicity, effective dates, authority, source_document, source_url,
--     verification_date, release_status) PLUS a rule_payload TEXT column holding
--     the structured statutory values as JSON (same JSON-as-TEXT approach as
--     import_session / payroll calculation_basis). This avoids an over-engineered
--     per-rate schema while keeping provenance queryable and reproducible.
--   * release_status uses VARCHAR + CHECK (DRAFT/VERIFIED/SUPERSEDED) - no native
--     ENUM (H2 compatibility). DRAFT rows may hold a null/partial payload;
--     VERIFIED/SUPERSEDED immutability is enforced in the SERVICE layer (the DB
--     does not distinguish an UPDATE by status).
--   * Overlap of VERIFIED periods per (rule_type[/state], jurisdiction) is a
--     conditional invariant enforced in the SERVICE layer (portable to H2; no
--     partial unique index), with a supporting lookup index.
--   * A rule row belongs to exactly one rule version set (FK). One rule row per
--     (version set, rule type[, pt_state]) is enforced with a plain composite
--     UNIQUE (portable).
--   * Tamil Nadu PT capability: pt_state + local_body + periodicity columns let a
--     PT rule express a half-yearly, local-body jurisdiction (TN) rather than
--     being forced into a monthly-state model. No TN values are inserted here.
--
-- Also seeds a new PERMISSION 'statutory.release' granted ONLY to the platform
-- SUPER_ADMIN role. Statutory release data is platform/release authority;
-- COMPANY_ADMIN and PAYROLL_ADMIN do not receive it (so they are forbidden from
-- managing rule values). V4 seed is immutable; this is an additive seed.
--
-- V1-V18 are not modified.
-- =============================================================================

-- 1. Statutory release permission (platform authority) ------------------------
INSERT INTO permission (id, code, description, created_at) VALUES
  ('00000000-0000-0000-0000-000000000210', 'statutory.release',
   'Provision and release verified statutory rule data (platform authority)',
   CURRENT_TIMESTAMP);

-- Grant to SUPER_ADMIN role only (role id from V4 seed).
INSERT INTO role_permission (id, role_id, permission_id) VALUES
  ('00000000-0000-0000-0000-000000000309',
   '00000000-0000-0000-0000-000000000101',
   '00000000-0000-0000-0000-000000000210');

-- 2. PF rule values -----------------------------------------------------------
CREATE TABLE statutory_pf_rule (
    id                   UUID          NOT NULL,
    rule_version_set_id  UUID          NOT NULL,
    rule_type            VARCHAR(10)   NOT NULL,
    jurisdiction         VARCHAR(100)  NOT NULL,
    effective_from       DATE          NOT NULL,
    effective_to         DATE          NULL,
    authority            VARCHAR(255)  NULL,
    source_document      VARCHAR(500)  NULL,
    source_url           VARCHAR(1000) NULL,
    verification_date    DATE          NULL,
    release_status       VARCHAR(12)   NOT NULL,
    rule_payload         TEXT          NULL,
    created_by           UUID          NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    verified_by          UUID          NULL,
    verified_at          TIMESTAMP     NULL,
    CONSTRAINT pk_statutory_pf_rule PRIMARY KEY (id),
    CONSTRAINT fk_pf_rule_version_set
        FOREIGN KEY (rule_version_set_id) REFERENCES statutory_rule_version_set (id),
    CONSTRAINT fk_pf_rule_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_pf_rule_verified_by FOREIGN KEY (verified_by) REFERENCES app_user (id),
    CONSTRAINT uq_pf_rule_version_set UNIQUE (rule_version_set_id),
    CONSTRAINT ck_pf_rule_type CHECK (rule_type = 'PF'),
    CONSTRAINT ck_pf_rule_release_status
        CHECK (release_status IN ('DRAFT', 'VERIFIED', 'SUPERSEDED'))
);

CREATE INDEX ix_pf_rule_lookup
    ON statutory_pf_rule (jurisdiction, effective_from, release_status);

-- 3. PT rule values (per state; TN may carry local_body + half-yearly) --------
CREATE TABLE statutory_pt_rule (
    id                   UUID          NOT NULL,
    rule_version_set_id  UUID          NOT NULL,
    rule_type            VARCHAR(10)   NOT NULL,
    jurisdiction         VARCHAR(100)  NOT NULL,
    pt_state             VARCHAR(50)   NOT NULL,
    local_body           VARCHAR(100)  NULL,
    periodicity          VARCHAR(15)   NOT NULL,
    effective_from       DATE          NOT NULL,
    effective_to         DATE          NULL,
    authority            VARCHAR(255)  NULL,
    source_document      VARCHAR(500)  NULL,
    source_url           VARCHAR(1000) NULL,
    verification_date    DATE          NULL,
    release_status       VARCHAR(12)   NOT NULL,
    rule_payload         TEXT          NULL,
    created_by           UUID          NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    verified_by          UUID          NULL,
    verified_at          TIMESTAMP     NULL,
    CONSTRAINT pk_statutory_pt_rule PRIMARY KEY (id),
    CONSTRAINT fk_pt_rule_version_set
        FOREIGN KEY (rule_version_set_id) REFERENCES statutory_rule_version_set (id),
    CONSTRAINT fk_pt_rule_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_pt_rule_verified_by FOREIGN KEY (verified_by) REFERENCES app_user (id),
    CONSTRAINT uq_pt_rule_version_set_state UNIQUE (rule_version_set_id, pt_state),
    CONSTRAINT ck_pt_rule_type CHECK (rule_type = 'PT'),
    CONSTRAINT ck_pt_rule_state
        CHECK (pt_state IN ('MAHARASHTRA', 'KARNATAKA', 'TAMIL_NADU',
                            'TELANGANA', 'WEST_BENGAL')),
    CONSTRAINT ck_pt_rule_periodicity
        CHECK (periodicity IN ('MONTHLY', 'HALF_YEARLY')),
    CONSTRAINT ck_pt_rule_release_status
        CHECK (release_status IN ('DRAFT', 'VERIFIED', 'SUPERSEDED'))
);

CREATE INDEX ix_pt_rule_lookup
    ON statutory_pt_rule (pt_state, effective_from, release_status);

-- 4. TDS rule values (New Regime automatic, v0) -------------------------------
CREATE TABLE statutory_tds_rule (
    id                   UUID          NOT NULL,
    rule_version_set_id  UUID          NOT NULL,
    rule_type            VARCHAR(10)   NOT NULL,
    jurisdiction         VARCHAR(100)  NOT NULL,
    financial_year       VARCHAR(7)    NOT NULL,
    tax_regime           VARCHAR(30)   NOT NULL,
    effective_from       DATE          NOT NULL,
    effective_to         DATE          NULL,
    authority            VARCHAR(255)  NULL,
    source_document      VARCHAR(500)  NULL,
    source_url           VARCHAR(1000) NULL,
    verification_date    DATE          NULL,
    release_status       VARCHAR(12)   NOT NULL,
    rule_payload         TEXT          NULL,
    created_by           UUID          NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    verified_by          UUID          NULL,
    verified_at          TIMESTAMP     NULL,
    CONSTRAINT pk_statutory_tds_rule PRIMARY KEY (id),
    CONSTRAINT fk_tds_rule_version_set
        FOREIGN KEY (rule_version_set_id) REFERENCES statutory_rule_version_set (id),
    CONSTRAINT fk_tds_rule_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_tds_rule_verified_by FOREIGN KEY (verified_by) REFERENCES app_user (id),
    CONSTRAINT uq_tds_rule_version_set UNIQUE (rule_version_set_id),
    CONSTRAINT ck_tds_rule_type CHECK (rule_type = 'TDS'),
    CONSTRAINT ck_tds_rule_regime CHECK (tax_regime = 'NEW_REGIME_AUTOMATIC_V0'),
    CONSTRAINT ck_tds_rule_release_status
        CHECK (release_status IN ('DRAFT', 'VERIFIED', 'SUPERSEDED'))
);

CREATE INDEX ix_tds_rule_lookup
    ON statutory_tds_rule (financial_year, effective_from, release_status);
