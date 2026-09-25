package com.example.HRMS.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import java.util.ArrayList;
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
 * Regression: RBAC read endpoints (roles, role-by-id, permissions, user-role
 * lookup) — status, DTO contract, deterministic ordering, and 404 behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacRegressionTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;

    private String superAdminAuth;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.resetIdentityData();
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
        superAdminAuth = "Bearer " + login("superadmin");
    }

    private String login(String username) throws Exception {
        String json = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\""
                                + RbacTestFixtures.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asString();
    }

    // --- roles ---
    @Test
    void getRoleByValidIdReturnsDtoWithoutEntityInternals() throws Exception {
        String json = mockMvc.perform(get("/api/v1/roles/" + RbacTestFixtures.ROLE_SUPER_ADMIN)
                        .header("Authorization", superAdminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.scopeType").value("PLATFORM"))
                .andExpect(jsonPath("$.assignable").value(true))
                .andReturn().getResponse().getContentAsString();
        // DTO must not leak persistence internals (e.g. Hibernate/JPA fields).
        assertThat(json).doesNotContain("createdAt").doesNotContain("hibernate");
    }

    @Test
    void getRoleByNonexistentIdReturns404WithErrorCode() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + UUID.randomUUID())
                        .header("Authorization", superAdminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void listRolesReturnsAllFiveSeededRolesDeterministicallyOrdered() throws Exception {
        String json = mockMvc.perform(get("/api/v1/roles").header("Authorization", superAdminAuth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(json);
        var codes = new ArrayList<String>();
        arr.forEach(n -> codes.add(n.get("code").asString()));
        assertThat(codes).containsExactlyInAnyOrder(
                "SUPER_ADMIN", "COMPANY_ADMIN", "PAYROLL_ADMIN", "EMPLOYEE", "MANAGER");
        assertThat(codes).isSorted();
    }

    // --- permissions ---
    @Test
    void listPermissionsAuthorizedIsDeterministicallyOrdered() throws Exception {
        String json = mockMvc.perform(get("/api/v1/permissions").header("Authorization", superAdminAuth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(json);
        var codes = new ArrayList<String>();
        arr.forEach(n -> codes.add(n.get("code").asString()));
        // 9 V4-seeded permissions + statutory.release (V19, Phase 2) = 10.
        assertThat(codes).hasSize(10).isSorted();
        // Response contract: id/code/description only.
        assertThat(arr.get(0).has("id")).isTrue();
        assertThat(arr.get(0).has("description")).isTrue();
    }

    @Test
    void listPermissionsUnauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- user role lookup ---
    @Test
    void getRolesForNonexistentUserReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/roles")
                        .header("Authorization", superAdminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
