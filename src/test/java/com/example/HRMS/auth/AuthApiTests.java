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
        jdbcTemplate.update("DELETE FROM statutory_configuration");
        jdbcTemplate.update("DELETE FROM user_role");
        userRepository.deleteAll();
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
}
