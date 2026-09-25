-- =============================================================================
-- V20 - Compensation Dearness Allowance (DA) component (HRMS Payroll MVP, Phase 3)
--
-- Adds da_monthly to compensation_record as an explicit, fixed recurring monthly
-- compensation component (V2-009.1A DA closure). DA is effective-dated and
-- calendar-day prorated exactly like Basic / HRA / Other Fixed Allowances, and
-- is part of pre-statutory gross earnings.
--
-- Design notes (consistent with V12 and AGENTS.md 8):
--   * Monetary persistence convention NUMERIC(18,2), never floating point.
--   * NOT NULL with DEFAULT 0 so existing compensation rows remain valid with
--     DA = 0 (additive, backward-compatible).
--   * Non-negative enforced by a CHECK constraint, matching the existing
--     compensation component constraints (ck_compensation_*_non_negative).
--   * No statutory values are inserted. This migration does not touch PF/PT/TDS
--     rules; statutory_pf_rule / statutory_pt_rule / statutory_tds_rule remain
--     empty.
-- V1-V19 are not modified.
-- =============================================================================

ALTER TABLE compensation_record
    ADD COLUMN da_monthly NUMERIC(18,2) NOT NULL DEFAULT 0;

ALTER TABLE compensation_record
    ADD CONSTRAINT ck_compensation_da_non_negative CHECK (da_monthly >= 0);
