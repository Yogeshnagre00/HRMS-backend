package com.example.HRMS.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable authenticated principal derived entirely from server-side state
 * (the persisted user + role/permission assignments) — never from client input.
 *
 * <p>Placed in {@code common.security} as a shared, dependency-free value object
 * so that the security, auth and rbac modules can all reference the authenticated
 * context without depending on one another merely to share this type. It is put
 * into the Spring Security context as the authentication principal.
 */
public record AuthenticatedUser(
        UUID userId,
        String username,
        ScopeType scopeType,
        UUID companyId,
        boolean mustChangePassword,
        Set<String> roles,
        Set<String> permissions) {

    public boolean isPlatform() {
        return scopeType == ScopeType.PLATFORM;
    }

    public boolean hasPermission(String code) {
        return permissions.contains(code);
    }
}
