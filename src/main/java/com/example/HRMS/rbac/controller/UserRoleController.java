package com.example.HRMS.rbac.controller;

import com.example.HRMS.rbac.dto.RbacDtos.AuthorizationMeResponse;
import com.example.HRMS.rbac.dto.RbacDtos.ReplaceUserRolesRequest;
import com.example.HRMS.rbac.dto.RbacDtos.UserRolesResponse;
import com.example.HRMS.rbac.service.RbacService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-role assignment and effective-authorization API (API spec section 6).
 * Thin controller; scope and escalation guards live in {@link RbacService}. It
 * performs no repository access.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "RBAC", description = "User role assignments and effective authorization")
public class UserRoleController {

    private final RbacService rbacService;
    private final CurrentUser currentUser;

    public UserRoleController(RbacService rbacService, CurrentUser currentUser) {
        this.rbacService = rbacService;
        this.currentUser = currentUser;
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('user.read')")
    @Operation(summary = "Read user-role assignments")
    public ResponseEntity<UserRolesResponse> getUserRoles(@PathVariable UUID userId) {
        return ResponseEntity.ok(rbacService.getUserRoles(currentUser.require(), userId));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('user.admin')")
    @Operation(summary = "Assign/replace user roles")
    public ResponseEntity<UserRolesResponse> replaceUserRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplaceUserRolesRequest request) {
        return ResponseEntity.ok(
                rbacService.replaceUserRoles(currentUser.require(), userId, request.roleIds()));
    }

    @GetMapping("/authorization/me")
    @Operation(summary = "Return effective permissions/scope for current user")
    public ResponseEntity<AuthorizationMeResponse> authorizationMe() {
        return ResponseEntity.ok(rbacService.authorizationMe(currentUser.require()));
    }
}
