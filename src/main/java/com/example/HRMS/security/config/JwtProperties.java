package com.example.HRMS.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from {@code hrms.security.jwt.*}.
 *
 * <p>The signing {@code secret} must come from the environment
 * ({@code HRMS_JWT_SECRET}) in real deployments; a development-only fallback is
 * defined in {@code application.properties} for local/test runs.
 */
@ConfigurationProperties(prefix = "hrms.security.jwt")
public class JwtProperties {

    /** HMAC signing secret (must be long enough for HS256). */
    private String secret;

    /** Access token lifetime in minutes. */
    private long accessTokenMinutes = 60;

    /** MFA challenge (pre-auth) token lifetime in minutes. */
    private long mfaChallengeMinutes = 5;

    /** Refresh token lifetime in minutes (longer-lived than the access token). */
    private long refreshTokenMinutes = 60 * 24 * 7; // 7 days

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(long accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public long getMfaChallengeMinutes() {
        return mfaChallengeMinutes;
    }

    public void setMfaChallengeMinutes(long mfaChallengeMinutes) {
        this.mfaChallengeMinutes = mfaChallengeMinutes;
    }

    public long getRefreshTokenMinutes() {
        return refreshTokenMinutes;
    }

    public void setRefreshTokenMinutes(long refreshTokenMinutes) {
        this.refreshTokenMinutes = refreshTokenMinutes;
    }
}
