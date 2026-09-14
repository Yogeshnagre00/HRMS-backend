-- =============================================================================
-- V6 - Refresh token support (HRMS Payroll MVP, V0-003.4)
--
-- Server-side tracked, rotating refresh tokens. Each row is an opaque refresh
-- token: the token value IS the primary key (a random UUID string returned to
-- the client). Rotation on refresh revokes the old row and inserts a new one;
-- logout and password change revoke tokens. token_version mirrors the user's
-- token_version at issue time so a password change invalidates refresh tokens.
--
-- Additive schema change only. V1-V5 are not modified.
-- =============================================================================
CREATE TABLE refresh_token (
    id             VARCHAR(64) NOT NULL,
    user_id        UUID        NOT NULL,
    token_version  INTEGER     NOT NULL,
    issued_at      TIMESTAMP   NOT NULL,
    expires_at     TIMESTAMP   NOT NULL,
    revoked_at     TIMESTAMP   NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);
