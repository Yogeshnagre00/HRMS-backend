package com.example.HRMS.common.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consistent error/validation response contract shared across all API modules.
 *
 * <p>This is the single success/error envelope required by the API &amp; Backend
 * Requirements Specification (Section 4 - "Use one consistent success/error contract
 * across modules"). It carries enough information for clients to display validation
 * failures without exposing internal implementation details.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    /** A single field-level validation violation. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, List.of());
    }

    public static ApiError of(int status, String error, String message, String path,
                              List<FieldViolation> fieldErrors) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path,
                fieldErrors == null ? List.of() : fieldErrors);
    }
}
