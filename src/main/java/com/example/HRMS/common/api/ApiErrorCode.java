package com.example.HRMS.common.api;

/**
 * Stable, machine-readable error codes returned in {@link ApiError#code()}.
 *
 * <p>Clients branch on these codes rather than parsing the human-readable
 * message. Codes are HTTP-outcome oriented for the foundation; modules may add
 * more specific codes as their error taxonomy grows.
 */
public enum ApiErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR
}
