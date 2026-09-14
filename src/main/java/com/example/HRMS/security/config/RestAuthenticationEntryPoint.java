package com.example.HRMS.security.config;

import com.example.HRMS.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits a 401 response in the shared {@link ApiError} shape when an
 * unauthenticated request reaches a protected endpoint. Distinct from the 403
 * access-denied handler so callers can tell "not authenticated" from
 * "authenticated but forbidden".
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                com.example.HRMS.common.api.ApiErrorCode.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                com.example.HRMS.common.api.ApiMessages.AUTH_REQUIRED_TO_ACCESS,
                request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
