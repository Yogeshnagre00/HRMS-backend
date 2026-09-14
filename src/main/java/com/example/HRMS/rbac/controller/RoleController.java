package com.example.HRMS.rbac.controller;

import com.example.HRMS.rbac.dto.RbacDtos.PermissionResponse;
import com.example.HRMS.rbac.dto.RbacDtos.RoleResponse;
import com.example.HRMS.rbac.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles and permissions read API (API spec section 6). Thin controller;
 * permission checks are declared with method security and all logic lives in
 * {@link RbacService}. It performs no repository access.
 *
 * <p>In v0 roles and their permissions are system-defined (seeded); there are no
 * APIs to create roles, edit roles, or change role-permission mappings.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "RBAC", description = "Roles and permissions catalogue (read-only)")
public class RoleController {

    private final RbacService rbacService;

    public RoleController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role.read')")
    @Operation(summary = "List roles")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(rbacService.listRoles());
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role.read')")
    @Operation(summary = "Read role")
    public ResponseEntity<RoleResponse> getRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(rbacService.getRole(roleId));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('permission.read')")
    @Operation(summary = "List permissions")
    public ResponseEntity<List<PermissionResponse>> listPermissions() {
        return ResponseEntity.ok(rbacService.listPermissions());
    }
}
