package com.example.HRMS.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A server-side tracked refresh token (table {@code refresh_token}).
 *
 * <p>The token value {@code id} is opaque (a random UUID string) and is the
 * primary key. Refresh tokens are rotated on use (the old one is revoked and a
 * new one issued), revoked on logout, and invalidated when the user's
 * {@code tokenVersion} changes (e.g. password change).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(String id, UUID userId, int tokenVersion,
                        LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenVersion = tokenVersion;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
