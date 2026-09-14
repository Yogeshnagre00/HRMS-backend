package com.example.HRMS.auth.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.auth.dto.AuthDtos.LoginRequest;
import com.example.HRMS.auth.dto.AuthDtos.LoginResponse;
import com.example.HRMS.auth.dto.AuthDtos.MeResponse;
import com.example.HRMS.auth.dto.AuthDtos.MfaVerifyRequest;
import com.example.HRMS.auth.dto.AuthDtos.PasswordChangeRequest;
import com.example.HRMS.auth.dto.AuthDtos.UserSummary;
import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.auth.service.RefreshTokenService.RotationResult;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.security.core.JwtService;
import com.example.HRMS.security.core.JwtService.IssuedToken;
import com.example.HRMS.security.core.JwtService.ParsedToken;
import com.example.HRMS.security.core.TotpVerifier;
import com.example.HRMS.security.token.RevokedToken;
import com.example.HRMS.security.token.RevokedTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use cases: login (with MFA branch), MFA verification, token
 * refresh, logout, current-identity, and password change. Owned by the
 * {@code auth} module.
 *
 * <p>Security properties: passwords are BCrypt-verified and never logged,
 * returned, or audited; login failures do not reveal whether the username
 * exists; password change bumps the user's token version to invalidate all
 * previously issued access tokens and revokes all refresh tokens. Successful
 * authentication issues a short-lived access token plus a longer-lived,
 * server-tracked, rotating refresh token. Security-sensitive events are audited.
 *
 * <p>Roles/permissions are resolved by the RBAC module: on protected requests
 * via the {@link AuthenticatedUser} principal, and for the login summary via the
 * {@link UserRolesLookup} port (implemented by RBAC), so this service does not
 * depend on the RBAC module directly.
 */
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpVerifier totpVerifier;
    private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRolesLookup userRolesLookup;
    private final AuditService auditService;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TotpVerifier totpVerifier,
                       RevokedTokenRepository revokedTokenRepository,
                       RefreshTokenService refreshTokenService,
                       UserRolesLookup userRolesLookup,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpVerifier = totpVerifier;
        this.revokedTokenRepository = revokedTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.userRolesLookup = userRolesLookup;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Optional<AppUser> maybeUser = userRepository.findByUsername(request.username());

        // Uniform failure handling: do not reveal whether the username exists.
        if (maybeUser.isEmpty()
                || maybeUser.get().getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), maybeUser.get().getPasswordHash())) {
            maybeUser.ifPresent(u -> auditService.record(new AuditEvent(
                    u.getId(), u.getCompanyId(), u.getScopeType(),
                    AuditActions.LOGIN_FAILURE, AuditActions.ENTITY_USER, u.getId(),
                    "FAILURE", "Invalid credentials or inactive account", null)));
            throw ApiException.unauthorized(ApiMessages.AUTH_INVALID_CREDENTIALS);
        }

        AppUser user = maybeUser.get();

        if (user.isMfaEnabled()) {
            IssuedToken challenge = jwtService.issueMfaChallengeToken(
                    user.getId(), user.getUsername(), user.getTokenVersion());
            return LoginResponse.mfaRequired(challenge.token());
        }

        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.LOGIN_SUCCESS, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
        return issueAuthenticatedResponse(user);
    }

    @Transactional
    public LoginResponse verifyMfa(MfaVerifyRequest request) {
        ParsedToken parsed;
        try {
            parsed = jwtService.parse(request.mfaToken());
        } catch (Exception ex) {
            throw ApiException.unauthorized(ApiMessages.AUTH_MFA_CHALLENGE_INVALID_OR_EXPIRED);
        }
        if (!JwtService.TYPE_MFA_CHALLENGE.equals(parsed.type())) {
            throw ApiException.unauthorized(ApiMessages.AUTH_MFA_CHALLENGE_TOKEN_INVALID);
        }
        AppUser user = userRepository.findById(parsed.userId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.unauthorized(ApiMessages.AUTH_MFA_CHALLENGE_INVALID));

        if (!totpVerifier.verify(user.getMfaSecret(), request.code())) {
            auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                    AuditActions.MFA_FAILURE, AuditActions.ENTITY_USER, user.getId(), "FAILURE"));
            throw ApiException.unauthorized(ApiMessages.AUTH_MFA_CODE_INVALID);
        }

        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.MFA_SUCCESS, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.LOGIN_SUCCESS, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
        return issueAuthenticatedResponse(user);
    }

    /** Exchange a valid refresh token for a new access token + rotated refresh token. */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        RotationResult result = refreshTokenService.rotate(refreshToken);
        AppUser user = result.user();
        return LoginResponse.authenticated(
                result.accessToken(),
                result.refreshToken(),
                refreshTokenService.accessTokenExpiresInSeconds(),
                user.isMustChangePassword(),
                userSummary(user));
    }

    @Transactional
    public void logout(AuthenticatedUser principal, String bearerToken, String refreshToken) {
        // Revoke the presented access token (JWT denylist) and refresh token.
        if (bearerToken != null) {
            try {
                ParsedToken parsed = jwtService.parse(bearerToken);
                if (!revokedTokenRepository.existsByJti(parsed.jti())) {
                    revokedTokenRepository.save(new RevokedToken(
                            parsed.jti(),
                            parsed.userId(),
                            LocalDateTime.ofInstant(parsed.expiresAt(), ZoneOffset.UTC),
                            LocalDateTime.now()));
                }
            } catch (Exception ex) {
                // Access token already invalid; nothing to revoke there.
            }
        }
        refreshTokenService.revoke(refreshToken);
        auditService.record(AuditEvent.of(principal.userId(), principal.scopeType(),
                AuditActions.LOGOUT, AuditActions.ENTITY_USER, principal.userId(), "SUCCESS"));
    }

    /** Current identity/context. Roles and permissions are read from the principal. */
    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser principal) {
        return new MeResponse(
                principal.userId(),
                principal.username(),
                principal.scopeType().name(),
                principal.companyId(),
                principal.mustChangePassword(),
                principal.roles().stream().sorted().toList(),
                principal.permissions().stream().sorted().toList());
    }

    @Transactional
    public void changePassword(AuthenticatedUser principal, PasswordChangeRequest request) {
        AppUser user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized(ApiMessages.AUTH_AUTHENTICATION_REQUIRED));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest(ApiMessages.AUTH_CURRENT_PASSWORD_INCORRECT);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Invalidate all previously issued access tokens and clear the bootstrap flag.
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all outstanding refresh tokens for the user.
        refreshTokenService.revokeAllForUser(user.getId());

        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.PASSWORD_CHANGE, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
    }

    /** Build the authenticated response (access + rotated refresh token + summary). */
    private LoginResponse issueAuthenticatedResponse(AppUser user) {
        IssuedToken access = jwtService.issueAccessToken(
                user.getId(), user.getUsername(), user.getTokenVersion());
        String refreshToken = refreshTokenService.issue(user);
        return LoginResponse.authenticated(
                access.token(),
                refreshToken,
                refreshTokenService.accessTokenExpiresInSeconds(),
                user.isMustChangePassword(),
                userSummary(user));
    }

    private UserSummary userSummary(AppUser user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getScopeType().name(),
                userRolesLookup.roleCodesForUser(user.getId()));
    }
}
