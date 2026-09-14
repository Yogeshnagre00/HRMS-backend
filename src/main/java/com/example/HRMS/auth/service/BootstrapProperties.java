package com.example.HRMS.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the initial platform Super Admin bootstrap, bound from
 * {@code hrms.security.bootstrap.*}.
 *
 * <p>Credentials are supplied via configuration/environment only; there is no
 * hardcoded password in source. The initial password (from
 * {@code HRMS_BOOTSTRAP_ADMIN_PASSWORD}) is a BOOTSTRAP credential: the created
 * admin is flagged {@code mustChangePassword} and must rotate it after first
 * login. When no password is provided the bootstrap is skipped.
 */
@ConfigurationProperties(prefix = "hrms.security.bootstrap")
public class BootstrapProperties {

    /** Whether the startup Super Admin bootstrap runs. */
    private boolean enabled = true;

    /** Bootstrap admin username. */
    private String username = "admin";

    /**
     * Bootstrap admin initial password. Intentionally has NO default — it must be
     * supplied via {@code HRMS_BOOTSTRAP_ADMIN_PASSWORD}. When blank, the
     * bootstrap is skipped rather than creating a user with a hardcoded password.
     */
    private String password = "";

    /** Bootstrap admin email. */
    private String email = "admin@example.com";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
