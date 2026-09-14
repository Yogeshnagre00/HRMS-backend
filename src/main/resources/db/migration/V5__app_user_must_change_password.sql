-- =============================================================================
-- V5 - Bootstrap password support (HRMS Payroll MVP, V0-003 correction)
--
-- Adds a flag indicating the user must change their password before continuing.
-- It is set for the bootstrap Super Admin (created at application startup with a
-- known initial password) so the admin is required to rotate it after first
-- login, and cleared on any successful password change.
--
-- This is a SCHEMA change only (a new column). No user rows are inserted here;
-- the bootstrap Super Admin is provisioned by an idempotent startup mechanism,
-- not by a migration. V1-V4 are not modified.
-- =============================================================================
ALTER TABLE app_user
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
