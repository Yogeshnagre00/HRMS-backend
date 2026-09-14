package com.example.HRMS.security.core;

import com.example.HRMS.common.security.AuthenticatedUser;
import java.util.Optional;

/**
 * Resolves a validated access token's claims into an {@link AuthenticatedUser}
 * principal.
 *
 * <p>This seam keeps the {@code security} module free of any dependency on the
 * identity ({@code auth}) and authorization ({@code rbac}) domain modules: the
 * JWT filter validates the token cryptographically and then delegates
 * principal construction (loading the user, checking status/token-version, and
 * resolving roles/permissions) to an implementation owned by the domain side.
 * It has a concrete current purpose (breaking the security↔domain cycle) and is
 * not a speculative abstraction.
 */
public interface AuthenticationPrincipalResolver {

    /**
     * Build the principal for a verified ACCESS token, or empty if the token must
     * not authenticate (unknown/disabled user, stale token version, etc.).
     */
    Optional<AuthenticatedUser> resolve(JwtService.ParsedToken token);
}
