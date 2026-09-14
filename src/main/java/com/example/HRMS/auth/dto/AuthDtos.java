package com.example.HRMS.auth.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Request/response DTOs for the authentication API. Persistence entities are
 * never exposed directly; these records are the API contract.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    /** Compact identity summary returned with a successful authentication. */
    public record UserSummary(
            UUID id,
            String username,
            String scope,
            List<String> roles) {
    }

    /**
     * Login/authentication result. When MFA is required, {@code status=MFA_REQUIRED}
     * and a short-lived {@code mfaToken} is returned instead of tokens. On success,
     * {@code accessToken} + {@code refreshToken} are returned with {@code tokenType}
     * ("Bearer") and {@code expiresIn} (access-token lifetime in seconds).
     */
    public record LoginResponse(
            String status,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            String mfaToken,
            boolean mustChangePassword,
            UserSummary user) {

        public static LoginResponse authenticated(String accessToken, String refreshToken,
                                                  long expiresIn, boolean mustChangePassword,
                                                  UserSummary user) {
            return new LoginResponse("AUTHENTICATED", accessToken, refreshToken, "Bearer",
                    expiresIn, null, mustChangePassword, user);
        }

        public static LoginResponse mfaRequired(String mfaToken) {
            return new LoginResponse("MFA_REQUIRED", null, null, null, 0, mfaToken, false, null);
        }
    }

    public record MfaVerifyRequest(
            @NotBlank String mfaToken,
            @NotBlank String code) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    /** The authenticated user's identity/context (never includes secrets). */
    public record MeResponse(
            UUID userId,
            String username,
            String scopeType,
            UUID companyId,
            boolean mustChangePassword,
            List<String> roles,
            List<String> permissions) {
    }
}
