package com.example.HRMS.security.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A revoked JWT id (table {@code revoked_token}).
 *
 * <p>Used as a denylist so logout (and, if needed, other events) can invalidate
 * a specific still-valid token before its natural expiry.
 */
@Entity
@Table(name = "revoked_token")
public class RevokedToken {

    @Id
    @Column(name = "jti", nullable = false, updatable = false)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    protected RevokedToken() {
        // JPA
    }

    public RevokedToken(String jti, UUID userId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public String getJti() {
        return jti;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
