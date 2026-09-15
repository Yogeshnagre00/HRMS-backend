package com.example.HRMS.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.security.ScopeType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AUTH acceptance tests: valid login, invalid password, unknown identity,
 * disabled user, protected endpoint without auth, authenticated identity,
 * password change (token revocation), logout, and MFA-required behavior.
 * Aligns with acceptance scenarios SEC-001 and SEC-006.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID platformUserId;

    @BeforeEach
    void setUp() {
        // Delete child rows that reference app_user before deleting users.
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM revoked_token");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM statutory_configuration");
        // Employee-owned tables that reference app_user (created_by/actor) must be
        // cleared before app_user, in FK-safe order.
        jdbcTemplate.update("DELETE FROM payroll_run");
        jdbcTemplate.update("DELETE FROM leave_entry");
        jdbcTemplate.update("DELETE FROM attendance_exception");
        jdbcTemplate.update("DELETE FROM work_calendar_assignment");
        jdbcTemplate.update("DELETE FROM work_calendar");
        jdbcTemplate.update("DELETE FROM compensation_record");
        jdbcTemplate.update("DELETE FROM employee_leave_balance");
        jdbcTemplate.update("DELETE FROM employee_opening_tax_state");
        jdbcTemplate.update("DELETE FROM employee_bank_account");
        jdbcTemplate.update("DELETE FROM import_session_row");
        jdbcTemplate.update("DELETE FROM import_session");
        jdbcTemplate.update("DELETE FROM employee");
        jdbcTemplate.update("DELETE FROM legal_entity");
        jdbcTemplate.update("DELETE FROM user_role");
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM company");
        platformUserId = createUser("superadmin", "Str0ngPass!", UserStatus.ACTIVE, false, null);
        createUser("disabled", "Str0ngPass!", UserStatus.DISABLED, false, null);
        // MFA user with a known TOTP secret (Base32 of "12345678901234567890").
        createMfaUser("mfauser", "Str0ngPass!", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    private UUID createUser(String username, String password, UserStatus status,
                            boolean mfaEnabled, String mfaSecret) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(status);
        user.setScopeType(ScopeType.PLATFORM);
        user.setCompanyId(null);
        user.setMfaEnabled(mfaEnabled);
        user.setMfaSecret(mfaSecret);
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user).getId();
    }

    private void createMfaUser(String username, String password, String secret) {
        createUser(username, password, UserStatus.ACTIVE, true, secret);
    }

    private String login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("accessToken").asString();
    }

    @Test
    void validLoginReturnsAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"superadmin\",\"password\":\"Str0ngPass!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void invalidPasswordIsUnauthorizedAndDoesNotRevealUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"superadmin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void unknownUserIsUnauthorizedWithSameMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"nobody\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"disabled\",\"password\":\"Str0ngPass!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutAuthenticationIs401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void authenticatedUserContextIsReturned() throws Exception {
        String token = login("superadmin", "Str0ngPass!");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("superadmin"))
                .andExpect(jsonPath("$.scopeType").value("PLATFORM"));
    }

    @Test
    void passwordChangeRevokesExistingTokens() throws Exception {
        String token = login("superadmin", "Str0ngPass!");
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Str0ngPass!\",\"newPassword\":\"N3wStr0ngPass!\"}"))
                .andExpect(status().isNoContent());

        // The old token must no longer authenticate (token version bumped).
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        // The new password works.
        assertThat(login("superadmin", "N3wStr0ngPass!")).isNotBlank();
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String token = login("superadmin", "Str0ngPass!");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mfaRequiredUserReceivesChallengeInsteadOfAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"mfauser\",\"password\":\"Str0ngPass!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.mfaToken").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void mfaChallengeTokenCannotAccessProtectedResources() throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"mfauser\",\"password\":\"Str0ngPass!\"}"))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = objectMapper.readTree(json).get("mfaToken").asString();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + mfaToken))
                .andExpect(status().isUnauthorized());
    }

    // --- Refresh token (V0-003.4) ------------------------------------------

    /** Perform a login and return the parsed response body. */
    private JsonNode loginResponse(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void loginReturnsRefreshTokenAndMetadata() throws Exception {
        JsonNode node = loginResponse("superadmin", "Str0ngPass!");
        assertThat(node.get("accessToken").asString()).isNotBlank();
        assertThat(node.get("refreshToken").asString()).isNotBlank();
        assertThat(node.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(node.get("expiresIn").asLong()).isPositive();
        assertThat(node.get("user").get("username").asString()).isEqualTo("superadmin");
        assertThat(node.get("user").get("scope").asString()).isEqualTo("PLATFORM");
    }

    @Test
    void refreshReturnsNewTokensAndAccessTokenWorks() throws Exception {
        String refreshToken = loginResponse("superadmin", "Str0ngPass!").get("refreshToken").asString();

        String json = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);

        String newAccess = node.get("accessToken").asString();
        String newRefresh = node.get("refreshToken").asString();
        assertThat(newAccess).isNotBlank();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refreshToken);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    void rotatedRefreshTokenIsSingleUse() throws Exception {
        String refreshToken = loginResponse("superadmin", "Str0ngPass!").get("refreshToken").asString();

        // First use succeeds (and rotates).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk());

        // Re-using the now-revoked token is rejected.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsRejectedAfterLogout() throws Exception {
        JsonNode login = loginResponse("superadmin", "Str0ngPass!");
        String access = login.get("accessToken").asString();
        String refreshToken = login.get("refreshToken").asString();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsRejectedAfterPasswordChange() throws Exception {
        JsonNode login = loginResponse("superadmin", "Str0ngPass!");
        String access = login.get("accessToken").asString();
        String refreshToken = login.get("refreshToken").asString();

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Str0ngPass!\",\"newPassword\":\"N3wStr0ngPass!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRefreshTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized());
    }
}
