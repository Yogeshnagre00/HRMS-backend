package com.example.HRMS.security.core;

import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.security.token.RevokedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on each request. If
 * the token is a valid, non-revoked ACCESS token it delegates principal
 * construction to an {@link AuthenticationPrincipalResolver} (implemented by the
 * domain side) and populates the security context with the resulting
 * {@link AuthenticatedUser} and its permission authorities.
 *
 * <p>Purely technical: the filter does token verification, type and revocation
 * checks only; it owns no identity or RBAC logic. Invalid/expired/revoked
 * tokens are ignored (no authentication set) so downstream authorization
 * produces 401/403 rather than trusting the token.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuthenticationPrincipalResolver principalResolver;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   RevokedTokenRepository revokedTokenRepository,
                                   AuthenticationPrincipalResolver principalResolver) {
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.principalResolver = principalResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authenticate(header.substring(7));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        JwtService.ParsedToken parsed;
        try {
            parsed = jwtService.parse(token);
        } catch (Exception ex) {
            return; // invalid/expired signature -> unauthenticated
        }

        // Only full ACCESS tokens authenticate; MFA challenge tokens do not.
        if (!JwtService.TYPE_ACCESS.equals(parsed.type())) {
            return;
        }
        if (revokedTokenRepository.existsByJti(parsed.jti())) {
            return; // logged out / revoked
        }

        Optional<AuthenticatedUser> maybePrincipal = principalResolver.resolve(parsed);
        if (maybePrincipal.isEmpty()) {
            return; // unknown/disabled user or stale token version
        }
        AuthenticatedUser principal = maybePrincipal.get();
        List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
