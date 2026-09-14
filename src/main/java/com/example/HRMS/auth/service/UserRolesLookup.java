package com.example.HRMS.auth.service;

import java.util.List;
import java.util.UUID;

/**
 * Port (owned by the {@code auth} module) for reading a user's role codes when
 * building an authentication response summary.
 *
 * <p>Implemented by the {@code rbac} module. Defining the interface here keeps
 * the dependency direction acyclic: {@code rbac} already depends on {@code auth},
 * so it may implement this port, whereas {@code auth} must not depend on
 * {@code rbac}. It has a concrete current purpose (populate the login user
 * summary's roles) and is not a speculative abstraction.
 */
public interface UserRolesLookup {

    /** Role codes assigned to the given user, sorted for stable output. */
    List<String> roleCodesForUser(UUID userId);
}
