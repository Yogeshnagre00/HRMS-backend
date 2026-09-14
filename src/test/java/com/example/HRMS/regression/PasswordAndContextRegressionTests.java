package com.example.HRMS.regression;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Regression: password-change security (mustChangePassword transition, wrong
 * current password, unauthorized change, old password invalidation) and
 * authenticated-context endpoints (/auth/me with invalid token, authorization/me
 * scope + permissions).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordAndContextRegressionTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        // Platform user flagged mustChangePassword (as the bootstrap admin would be).
        UUID mustChange = fixtures.createUser("mcp", ScopeType.PLATFORM, null, UserStatus.ACTIVE,
                false, null, true);
        fixtures.assignRole(mustChange, RbacTestFixtures.ROLE_SUPER_ADMIN);
        // Ordinary company payroll admin for authorization/me scope checks.
        UUID company = fixtures.insertCompany("Company A");
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, company, UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
    }

    private JsonNode login(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void loginSurfacesMustChangePasswordThenClearsAfterChange() throws Exception {
        JsonNode login = login("mcp", RbacTestFixtures.PASSWORD);
        // mustChangePassword flag is surfaced at login.
        org.assertj.core.api.Assertions.assertThat(login.get("mustChangePassword").asBoolean()).isTrue();
        String access = login.get("accessToken").asString();

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + RbacTestFixtures.PASSWORD
                                + "\",\"newPassword\":\"N3wStr0ngPass!\"}"))
                .andExpect(status().isNoContent());

        // New login clears the flag; old password no longer works.
        JsonNode relogin = login("mcp", "N3wStr0ngPass!");
        org.assertj.core.api.Assertions.assertThat(relogin.get("mustChangePassword").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"mcp\",\"password\":\"" + RbacTestFixtures.PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongCurrentPasswordIsBadRequest() throws Exception {
        String access = login("mcp", RbacTestFixtures.PASSWORD).get("accessToken").asString();
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"wrongwrong\",\"newPassword\":\"N3wStr0ngPass!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unauthenticatedPasswordChangeIs401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meWithInvalidTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authorizationMeReflectsServerSideScopeAndPermissions() throws Exception {
        String access = login("payadmin", RbacTestFixtures.PASSWORD).get("accessToken").asString();
        String json = mockMvc.perform(get("/api/v1/authorization/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("COMPANY"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode perms = objectMapper.readTree(json).get("permissions");
        var list = new java.util.ArrayList<String>();
        perms.forEach(p -> list.add(p.asString()));
        // PAYROLL_ADMIN => payroll.admin + audit.read, deterministically ordered.
        org.assertj.core.api.Assertions.assertThat(list).containsExactly("audit.read", "payroll.admin");
    }
}
