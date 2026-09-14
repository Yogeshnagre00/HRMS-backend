package com.example.HRMS.rbac.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.rbac.dto.RbacDtos.AuthorizationMeResponse;
import com.example.HRMS.rbac.dto.RbacDtos.PermissionResponse;
import com.example.HRMS.rbac.dto.RbacDtos.RoleResponse;
import com.example.HRMS.rbac.dto.RbacDtos.UserRolesResponse;
import com.example.HRMS.rbac.entity.Role;
import com.example.HRMS.rbac.entity.UserRole;
import com.example.HRMS.rbac.repository.PermissionRepository;
import com.example.HRMS.rbac.repository.RoleRepository;
import com.example.HRMS.rbac.repository.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC use cases: reading the system-defined role/permission catalogue, reading
 * and assigning user roles, and the effective-authorization view.
 *
 * <p>In v0 roles and their permission mappings are system-defined (seeded) and
 * are not created or modified through the API. All authorization context is
 * derived from the server-side authenticated principal. Guards prevent privilege
 * escalation: reserved roles (EMPLOYEE, MANAGER) cannot be assigned; the platform
 * SUPER_ADMIN role cannot be assigned by a company-scoped actor; a company-scoped
 * actor may only manage users within its own company. The user account is owned
 * by the {@code auth} module and is referenced here only to enforce scope.
 */
@Service
public class RbacService {

    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final AppUserRepository userRepository;
    private final AuditService auditService;

    public RbacService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       UserRoleRepository userRoleRepository,
                       AppUserRepository userRepository,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        // Deterministic ordering by stable role code (no sort parameter is exposed;
        // this is a small fixed reference collection).
        return roleRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Role::getCode))
                .map(RbacService::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .map(RbacService::toRoleResponse)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.ROLE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        // Deterministic ordering by stable permission code (small fixed reference
        // collection; no sort parameter is exposed).
        return permissionRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(
                        com.example.HRMS.rbac.entity.Permission::getCode))
                .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserRolesResponse getUserRoles(AuthenticatedUser actor, UUID userId) {
        AppUser target = loadUserInScope(actor, userId);
        List<UUID> roleIds = userRoleRepository.findByUserId(target.getId()).stream()
                .map(UserRole::getRoleId)
                .toList();
        List<String> codes = roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .sorted()
                .toList();
        return new UserRolesResponse(target.getId(), codes);
    }

    @Transactional
    public UserRolesResponse replaceUserRoles(AuthenticatedUser actor, UUID userId, List<UUID> roleIds) {
        AppUser target = loadUserInScope(actor, userId);
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.stream().distinct().count()) {
            throw ApiException.badRequest(ApiMessages.VALIDATION_ROLE_IDS_INVALID);
        }
        for (Role role : roles) {
            if (!role.isAssignable()) {
                throw ApiException.badRequest(ApiMessages.roleNotAssignable(role.getCode()));
            }
            // Only a platform actor may grant the platform SUPER_ADMIN role.
            if (SUPER_ADMIN_CODE.equals(role.getCode()) && !actor.isPlatform()) {
                throw ApiException.forbidden(ApiMessages.AUTHORIZATION_SUPER_ADMIN_PLATFORM_ONLY);
            }
        }
        userRoleRepository.deleteByUserId(target.getId());
        for (UUID roleId : roleIds.stream().distinct().toList()) {
            userRoleRepository.save(new UserRole(UUID.randomUUID(), target.getId(), roleId));
        }
        auditService.record(new AuditEvent(actor.userId(), target.getCompanyId(), actor.scopeType(),
                AuditActions.ROLE_ASSIGNMENT_CHANGE, AuditActions.ENTITY_USER, target.getId(),
                "SUCCESS", null, null));
        List<String> codes = roles.stream().map(Role::getCode).sorted().toList();
        return new UserRolesResponse(target.getId(), codes);
    }

    @Transactional(readOnly = true)
    public AuthorizationMeResponse authorizationMe(AuthenticatedUser actor) {
        return new AuthorizationMeResponse(
                actor.userId(),
                actor.scopeType().name(),
                actor.companyId(),
                actor.permissions().stream().sorted().toList());
    }

    /**
     * Load a target user, enforcing scope: a company-scoped actor may only act on
     * users within its own company; a platform actor may act on any user.
     */
    private AppUser loadUserInScope(AuthenticatedUser actor, UUID userId) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.USER_NOT_FOUND));
        if (!actor.isPlatform()) {
            if (target.getCompanyId() == null || !target.getCompanyId().equals(actor.companyId())) {
                // Do not disclose existence of out-of-scope resources.
                throw ApiException.notFound(ApiMessages.USER_NOT_FOUND);
            }
        }
        return target;
    }

    private static RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(),
                role.getScopeType().name(), role.isAssignable());
    }
}
