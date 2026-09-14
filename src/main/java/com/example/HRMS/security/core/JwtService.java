package com.example.HRMS.security.core;

import com.example.HRMS.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates stateless JWT bearer tokens (HS256).
 *
 * <p>Two token types are used:
 * <ul>
 *   <li>{@code ACCESS} — full authenticated token after login (and MFA when required).</li>
 *   <li>{@code MFA_CHALLENGE} — short-lived pre-auth token that only permits completing MFA.</li>
 * </ul>
 * The {@code tv} (token version) claim is compared against the user's current
 * {@code tokenVersion}; a password change bumps the version and invalidates all
 * previously issued tokens.
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_MFA_CHALLENGE = "MFA_CHALLENGE";

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_USERNAME = "usr";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final long accessTokenMinutes;
    private final long mfaChallengeMinutes;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = properties.getAccessTokenMinutes();
        this.mfaChallengeMinutes = properties.getMfaChallengeMinutes();
    }

    /**
     * Full access token issued once authentication (incl. MFA if required) is
     * complete. Takes primitive identity claims so the security module does not
     * depend on the identity entity.
     */
    public IssuedToken issueAccessToken(UUID userId, String username, int tokenVersion) {
        return issue(userId, username, tokenVersion, TYPE_ACCESS, accessTokenMinutes);
    }

    /** Short-lived challenge token that only authorises completing MFA. */
    public IssuedToken issueMfaChallengeToken(UUID userId, String username, int tokenVersion) {
        return issue(userId, username, tokenVersion, TYPE_MFA_CHALLENGE, mfaChallengeMinutes);
    }

    private IssuedToken issue(UUID userId, String username, int tokenVersion, String type,
                              long minutes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(minutes * 60);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiry);
    }

    /** Parse and cryptographically verify a token, returning its claims. */
    public ParsedToken parse(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        Claims claims = jws.getPayload();
        return new ParsedToken(
                claims.getId(),
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_TYPE, String.class),
                claims.get(CLAIM_TOKEN_VERSION, Integer.class),
                claims.getExpiration().toInstant());
    }

    /** A freshly issued token plus the metadata needed to revoke it later. */
    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    /** Verified claims extracted from a token. */
    public record ParsedToken(
            String jti, UUID userId, String username, String type, Integer tokenVersion,
            Instant expiresAt) {
    }
}
