package com.example.HRMS.rbac.service;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.security.core.AuthenticationPrincipalResolver;
import com.example.HRMS.security.core.JwtService;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain-side implementation of {@link AuthenticationPrincipalResolver}.
 *
 * <p>Loads the identity ({@code auth}), validates account status and token
 * version, and resolves roles/permissions ({@code rbac}) to build the
 * {@link AuthenticatedUser} principal. Living in the {@code rbac} module (which
 * legitimately depends on {@code auth} and {@code security}) keeps the
 * dependency direction acyclic: {@code security} depends only on {@code common}.
 */
@Component
public class RbacPrincipalResolver implements AuthenticationPrincipalResolver {

    private final AppUserRepository userRepository;
    private final AuthorizationContextService authorizationContextService;

    public RbacPrincipalResolver(AppUserRepository userRepository,
                                 AuthorizationContextService authorizationContextService) {
        this.userRepository = userRepository;
        this.authorizationContextService = authorizationContextService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> resolve(JwtService.ParsedToken token) {
        Optional<AppUser> maybeUser = userRepository.findById(token.userId());
        if (maybeUser.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = maybeUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty(); // disabled account
        }
        if (token.tokenVersion() == null || token.tokenVersion() != user.getTokenVersion()) {
            return Optional.empty(); // token invalidated by password change
        }
        return Optional.of(authorizationContextService.buildPrincipal(user));
    }
}
