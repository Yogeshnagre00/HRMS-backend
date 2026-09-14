package com.example.HRMS.rbac.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Request/response DTOs for the RBAC API (API spec section 6). */
public final class RbacDtos {

    private RbacDtos() {
    }

    public record RoleResponse(
            UUID id,
            String code,
            String name,
            String scopeType,
            boolean assignable) {
    }

    public record PermissionResponse(
            UUID id,
            String code,
            String description) {
    }

    public record ReplaceUserRolesRequest(
            @NotNull List<UUID> roleIds) {
    }

    public record UserRolesResponse(
            UUID userId,
            List<String> roleCodes) {
    }

    /** Effective permissions/scope for the current user (authorization/me). */
    public record AuthorizationMeResponse(
            UUID userId,
            String scopeType,
            UUID companyId,
            List<String> permissions) {
    }
}
