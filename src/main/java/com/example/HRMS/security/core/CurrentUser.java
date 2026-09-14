package com.example.HRMS.security.core;

import com.example.HRMS.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated {@link AuthenticatedUser} from the security context.
 *
 * <p>Technical security infrastructure: it exposes the server-side authenticated
 * identity to callers. Authorization context is always taken from this
 * server-side principal, never from request-supplied values.
 */
@Component
public class CurrentUser {

    /** The authenticated principal, or {@code null} if unauthenticated. */
    public AuthenticatedUser getOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    /** The authenticated principal or throw if absent (should be prevented by the filter chain). */
    public AuthenticatedUser require() {
        AuthenticatedUser user = getOrNull();
        if (user == null) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return user;
    }
}
