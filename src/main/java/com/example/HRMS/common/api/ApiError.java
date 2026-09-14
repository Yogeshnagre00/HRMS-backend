package com.example.HRMS.common.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consistent error/validation response contract shared across all API modules.
 *
 * <p>This is the single success/error envelope required by the Architecture, API
 * &amp; Governance Standards ("Consistent response/error contract",
 * "Module-specific message codes"). {@code code} is a stable, machine-readable
 * error code so clients can branch on error type without parsing the
 * human-readable {@code message}. {@code error} remains the HTTP reason phrase.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    /** A single field-level validation violation. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, ApiErrorCode code, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, code.name(), error, message, path, List.of());
    }

    public static ApiError of(int status, ApiErrorCode code, String error, String message, String path,
                              List<FieldViolation> fieldErrors) {
        return new ApiError(OffsetDateTime.now(), status, code.name(), error, message, path,
                fieldErrors == null ? List.of() : fieldErrors);
    }
}
