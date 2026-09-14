package com.example.HRMS.rbac.service;

import com.example.HRMS.auth.service.UserRolesLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC-side implementation of the {@link UserRolesLookup} port used by the auth
 * module to include role codes in the login response summary. Lives in
 * {@code rbac} (which may depend on {@code auth}), preserving acyclic module
 * dependencies.
 */
@Component
public class RbacUserRolesLookup implements UserRolesLookup {

    private final AuthorizationContextService authorizationContextService;

    public RbacUserRolesLookup(AuthorizationContextService authorizationContextService) {
        this.authorizationContextService = authorizationContextService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> roleCodesForUser(UUID userId) {
        return authorizationContextService.roleCodesForUser(userId).stream().sorted().toList();
    }
}
