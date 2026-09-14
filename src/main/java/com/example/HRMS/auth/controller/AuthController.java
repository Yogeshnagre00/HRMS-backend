package com.example.HRMS.auth.controller;

import com.example.HRMS.auth.dto.AuthDtos.LoginRequest;
import com.example.HRMS.auth.dto.AuthDtos.LoginResponse;
import com.example.HRMS.auth.dto.AuthDtos.MeResponse;
import com.example.HRMS.auth.dto.AuthDtos.MfaVerifyRequest;
import com.example.HRMS.auth.dto.AuthDtos.PasswordChangeRequest;
import com.example.HRMS.auth.dto.AuthDtos.RefreshRequest;
import com.example.HRMS.auth.service.AuthService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication API (API spec section 5). Thin controller: it delegates all
 * behavior to {@link AuthService} and holds no authentication, password, or
 * authorization logic.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, MFA, identity and password management")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user",
            description = "Verifies credentials and returns an access token, or an MFA challenge.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Complete MFA challenge",
            description = "Verifies the MFA code for a challenge token and returns access + refresh tokens.")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyMfa(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
            description = "Exchanges a valid refresh token for a new access token and a rotated "
                    + "refresh token. The presented refresh token is single-use.")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Terminate authenticated session/token",
            description = "Revokes the current access token and, if provided, the refresh token.")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @RequestBody(required = false) RefreshRequest body) {
        String refreshToken = body != null ? body.refreshToken() : null;
        authService.logout(currentUser.require(), bearerToken(request), refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return authenticated user/context")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(authService.me(currentUser.require()));
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password",
            description = "Changes the authenticated user's password and revokes existing tokens.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(currentUser.require(), request);
        return ResponseEntity.noContent().build();
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
