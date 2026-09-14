package com.example.HRMS.common.api;

import org.springframework.http.HttpStatus;

/**
 * Application exception carrying an HTTP status, a stable machine-readable
 * {@link ApiErrorCode}, and a safe client-facing message. Translated into the
 * shared {@link ApiError} contract by {@link GlobalExceptionHandler}. Messages
 * must not leak sensitive detail.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode code;

    public ApiException(HttpStatus status, ApiErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, message);
    }
}
