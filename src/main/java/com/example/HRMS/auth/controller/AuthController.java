package com.example.HRMS.auth.controller;

import com.example.HRMS.auth.dto.AuthDtos.LoginRequest;
import com.example.HRMS.auth.dto.AuthDtos.LoginResponse;
import com.example.HRMS.auth.dto.AuthDtos.MeResponse;
import com.example.HRMS.auth.dto.AuthDtos.MfaVerifyRequest;
import com.example.HRMS.auth.dto.AuthDtos.PasswordChangeRequest;
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
            description = "Verifies the MFA code for a challenge token and returns an access token.")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyMfa(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Terminate authenticated session/token")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(currentUser.require(), bearerToken(request));
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
