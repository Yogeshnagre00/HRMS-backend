package com.example.HRMS.common.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Centralised translation of exceptions into the common {@link ApiError} contract.
 *
 * <p>Foundation only: it handles request-shape/bean validation failures and a generic
 * fallback so every module produces the same response shape. Business-specific
 * exceptions are added by their owning modules in later tasks.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Body-level bean validation failures (e.g. {@code @Valid @RequestBody}). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldViolation)
                .toList();

        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields",
                path(request),
                violations);
        return ResponseEntity.badRequest().body(body);
    }

    /** Parameter/path/query bean validation failures ({@code @Validated} on beans). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toFieldViolation)
                .toList();

        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more parameters",
                path(request),
                violations);
        return ResponseEntity.badRequest().body(body);
    }

    /** Application exceptions carrying an explicit HTTP status and safe message. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, WebRequest request) {
        ApiError body = ApiError.of(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                path(request));
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Authorization denials from method security ({@code @PreAuthorize}) for an
     * already-authenticated user map to 403 (not the generic 500 fallback).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "You do not have permission to perform this action",
                path(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Generic fallback so unexpected errors still use the common contract. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred",
                path(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static ApiError.FieldViolation toFieldViolation(FieldError fieldError) {
        return new ApiError.FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static ApiError.FieldViolation toFieldViolation(ConstraintViolation<?> violation) {
        return new ApiError.FieldViolation(
                violation.getPropertyPath().toString(), violation.getMessage());
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
