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

    /**
     * Login result. When MFA is required, {@code status=MFA_REQUIRED} and a
     * short-lived {@code mfaToken} is returned instead of an access token.
     */
    public record LoginResponse(
            String status,
            String accessToken,
            String mfaToken,
            boolean mustChangePassword) {

        public static LoginResponse authenticated(String accessToken, boolean mustChangePassword) {
            return new LoginResponse("AUTHENTICATED", accessToken, null, mustChangePassword);
        }

        public static LoginResponse mfaRequired(String mfaToken) {
            return new LoginResponse("MFA_REQUIRED", null, mfaToken, false);
        }
    }

    public record MfaVerifyRequest(
            @NotBlank String mfaToken,
            @NotBlank String code) {
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
