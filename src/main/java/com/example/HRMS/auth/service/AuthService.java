package com.example.HRMS.auth.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.auth.dto.AuthDtos.LoginRequest;
import com.example.HRMS.auth.dto.AuthDtos.LoginResponse;
import com.example.HRMS.auth.dto.AuthDtos.MeResponse;
import com.example.HRMS.auth.dto.AuthDtos.MfaVerifyRequest;
import com.example.HRMS.auth.dto.AuthDtos.PasswordChangeRequest;
import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.api.ApiException;
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
 * Authentication use cases: login (with MFA branch), MFA verification, logout,
 * current-identity, and password change. Owned by the {@code auth} module.
 *
 * <p>Security properties: passwords are BCrypt-verified and never logged,
 * returned, or audited; login failures do not reveal whether the username
 * exists; password change bumps the user's token version to invalidate all
 * previously issued JWTs. Security-sensitive events are audited.
 *
 * <p>Roles/permissions are resolved by the RBAC module at authentication time
 * and carried on the {@link AuthenticatedUser} principal, so this service does
 * not depend on the RBAC module.
 */
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpVerifier totpVerifier;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuditService auditService;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TotpVerifier totpVerifier,
                       RevokedTokenRepository revokedTokenRepository,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpVerifier = totpVerifier;
        this.revokedTokenRepository = revokedTokenRepository;
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
            throw ApiException.unauthorized("Invalid username or password");
        }

        AppUser user = maybeUser.get();

        if (user.isMfaEnabled()) {
            IssuedToken challenge = jwtService.issueMfaChallengeToken(
                    user.getId(), user.getUsername(), user.getTokenVersion());
            return LoginResponse.mfaRequired(challenge.token());
        }

        IssuedToken access = jwtService.issueAccessToken(
                user.getId(), user.getUsername(), user.getTokenVersion());
        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.LOGIN_SUCCESS, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
        return LoginResponse.authenticated(access.token(), user.isMustChangePassword());
    }

    @Transactional
    public LoginResponse verifyMfa(MfaVerifyRequest request) {
        ParsedToken parsed;
        try {
            parsed = jwtService.parse(request.mfaToken());
        } catch (Exception ex) {
            throw ApiException.unauthorized("Invalid or expired MFA challenge");
        }
        if (!JwtService.TYPE_MFA_CHALLENGE.equals(parsed.type())) {
            throw ApiException.unauthorized("Invalid MFA challenge token");
        }
        AppUser user = userRepository.findById(parsed.userId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.unauthorized("Invalid MFA challenge"));

        if (!totpVerifier.verify(user.getMfaSecret(), request.code())) {
            auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                    AuditActions.MFA_FAILURE, AuditActions.ENTITY_USER, user.getId(), "FAILURE"));
            throw ApiException.unauthorized("Invalid MFA code");
        }

        IssuedToken access = jwtService.issueAccessToken(
                user.getId(), user.getUsername(), user.getTokenVersion());
        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.MFA_SUCCESS, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.LOGIN_SUCCESS, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
        return LoginResponse.authenticated(access.token(), user.isMustChangePassword());
    }

    @Transactional
    public void logout(AuthenticatedUser principal, String bearerToken) {
        if (bearerToken == null) {
            return;
        }
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
            // Token already invalid; nothing to revoke.
            return;
        }
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
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Invalidate all previously issued tokens and clear the bootstrap flag.
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.record(AuditEvent.of(user.getId(), user.getScopeType(),
                AuditActions.PASSWORD_CHANGE, AuditActions.ENTITY_USER, user.getId(), "SUCCESS"));
    }
}
