package com.example.HRMS.security.config;

import com.example.HRMS.security.core.AuthenticationPrincipalResolver;
import com.example.HRMS.security.core.JwtAuthenticationFilter;
import com.example.HRMS.security.core.JwtService;
import com.example.HRMS.security.token.RevokedTokenRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, JWT-based security configuration.
 *
 * <p>Authorization is permission-based via method security ({@code @PreAuthorize}
 * with {@code hasAuthority('permission.code')}). This chain only distinguishes
 * public endpoints (login, MFA challenge completion, health, API docs) from
 * everything else, which requires a valid access token. 401 vs 403 is handled by
 * dedicated handlers that use the shared {@code ApiError} contract.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            RevokedTokenRepository revokedTokenRepository,
            AuthenticationPrincipalResolver principalResolver) {
        return new JwtAuthenticationFilter(jwtService, revokedTokenRepository, principalResolver);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .csrf(csrf -> csrf.disable()) // stateless token API; no cookies/sessions
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: authentication entry points.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/mfa/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        // Public: liveness/health and API documentation.
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**")
                            .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        // Everything else requires authentication (+ permission via @PreAuthorize).
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
