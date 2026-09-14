package com.example.HRMS.auth.service;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.RefreshToken;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.auth.repository.RefreshTokenRepository;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.security.config.JwtProperties;
import com.example.HRMS.security.core.JwtService;
import com.example.HRMS.security.core.JwtService.IssuedToken;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages server-side tracked, rotating refresh tokens.
 *
 * <p>Security properties: refresh tokens are opaque random values stored (and
 * validated) server-side; on use they are <em>rotated</em> (the presented token
 * is revoked and a new one issued), so a stolen/replayed token is single-use;
 * they are revoked on logout and invalidated on password change (via the user's
 * {@code tokenVersion}). Refresh never returns a token for a disabled user or a
 * stale token version.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final long accessTokenMinutes;
    private final long refreshTokenMinutes;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               AppUserRepository userRepository,
                               JwtService jwtService,
                               JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.accessTokenMinutes = jwtProperties.getAccessTokenMinutes();
        this.refreshTokenMinutes = jwtProperties.getRefreshTokenMinutes();
    }

    /** Access-token lifetime in seconds (for the {@code expiresIn} response field). */
    public long accessTokenExpiresInSeconds() {
        return accessTokenMinutes * 60;
    }

    /** Issue a new refresh token for a user and return its opaque value. */
    @Transactional
    public String issue(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        String tokenValue = newOpaqueToken();
        refreshTokenRepository.save(new RefreshToken(
                tokenValue, user.getId(), user.getTokenVersion(),
                now, now.plusMinutes(refreshTokenMinutes)));
        return tokenValue;
    }

    /**
     * Validate and rotate a refresh token: revoke the presented token, verify the
     * user is active and the token version is current, then issue a fresh access
     * token and a new refresh token.
     */
    @Transactional
    public RotationResult rotate(String presentedToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken existing = refreshTokenRepository.findById(presentedToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (!existing.isActive(now)) {
            throw ApiException.unauthorized("Refresh token is expired or revoked");
        }

        AppUser user = userRepository.findById(existing.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (existing.getTokenVersion() != user.getTokenVersion()) {
            // Password/security change invalidated this token; revoke and reject.
            existing.setRevokedAt(now);
            refreshTokenRepository.save(existing);
            throw ApiException.unauthorized("Refresh token is no longer valid");
        }

        // Rotate: revoke the presented token, issue a new refresh token.
        existing.setRevokedAt(now);
        refreshTokenRepository.save(existing);

        IssuedToken access = jwtService.issueAccessToken(
                user.getId(), user.getUsername(), user.getTokenVersion());
        String newRefresh = issue(user);
        return new RotationResult(user, access.token(), newRefresh);
    }

    /** Revoke a specific refresh token (e.g. on logout). No-op if unknown/blank. */
    @Transactional
    public void revoke(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        Optional<RefreshToken> token = refreshTokenRepository.findById(presentedToken);
        token.ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(rt);
            }
        });
    }

    /** Revoke all active refresh tokens for a user (e.g. on password change). */
    @Transactional
    public void revokeAllForUser(java.util.UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, LocalDateTime.now());
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Result of a refresh rotation. */
    public record RotationResult(AppUser user, String accessToken, String refreshToken) {
    }
}
