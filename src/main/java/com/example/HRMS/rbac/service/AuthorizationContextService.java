package com.example.HRMS.rbac.service;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.rbac.entity.Permission;
import com.example.HRMS.rbac.entity.Role;
import com.example.HRMS.rbac.entity.RolePermission;
import com.example.HRMS.rbac.entity.UserRole;
import com.example.HRMS.rbac.repository.PermissionRepository;
import com.example.HRMS.rbac.repository.RolePermissionRepository;
import com.example.HRMS.rbac.repository.RoleRepository;
import com.example.HRMS.rbac.repository.UserRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a user's effective roles and permissions from persisted RBAC
 * assignments and builds the {@link AuthenticatedUser} principal.
 *
 * <p>This is RBAC domain/application behavior (it interprets role/permission
 * assignments), so it is owned by the {@code rbac} module — not by the technical
 * security infrastructure. Permissions are derived server-side only. The lookup
 * batches role and permission reads to avoid per-role N+1 queries.
 */
@Service
public class AuthorizationContextService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public AuthorizationContextService(UserRoleRepository userRoleRepository,
                                       RoleRepository roleRepository,
                                       RolePermissionRepository rolePermissionRepository,
                                       PermissionRepository permissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    /** Role ids assigned to a user. */
    @Transactional(readOnly = true)
    public List<UUID> roleIdsForUser(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .toList();
    }

    /** Effective permission codes granted to the user through all assigned roles. */
    @Transactional(readOnly = true)
    public Set<String> effectivePermissionCodes(UUID userId) {
        List<UUID> roleIds = roleIdsForUser(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> permissionIds = rolePermissionRepository.findByRoleIdIn(roleIds).stream()
                .map(RolePermission::getPermissionId)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        Map<UUID, String> codeById = permissionRepository.findAllById(permissionIds).stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getCode));
        return permissionIds.stream()
                .map(codeById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Role codes assigned to a user. */
    @Transactional(readOnly = true)
    public Set<String> roleCodesForUser(UUID userId) {
        List<UUID> roleIds = roleIdsForUser(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Build the authenticated principal for a user (roles + permissions + scope). */
    @Transactional(readOnly = true)
    public AuthenticatedUser buildPrincipal(AppUser user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getScopeType(),
                user.getCompanyId(),
                user.isMustChangePassword(),
                roleCodesForUser(user.getId()),
                effectivePermissionCodes(user.getId()));
    }
}
