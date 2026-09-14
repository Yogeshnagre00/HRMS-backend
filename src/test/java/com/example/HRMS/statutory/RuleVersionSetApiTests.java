package com.example.HRMS.statutory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.regression.RbacTestFixtures;
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
 * V0-005 Statutory Rule Version Set API tests: read-only list + get-by-id,
 * deterministic ordering, 404 for unknown id, and authorization (401/403).
 * There is intentionally no create/update endpoint for rule version sets.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleVersionSetApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;

    private UUID setId;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
        UUID payCompany = fixtures.insertCompany("Scoped Co", "INACTIVE");
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, payCompany, UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        setId = fixtures.insertRuleVersionSet("VERIFIED");
    }

    private String bearer(String username) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\""
                                + RbacTestFixtures.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(json).get("accessToken").asString();
    }

    @Test
    void listRuleVersionSets200() throws Exception {
        JsonNode list = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/statutory/rule-versions").header("Authorization", bearer("superadmin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("status").asString()).isEqualTo("VERIFIED");
    }

    @Test
    void getRuleVersionSetById200() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/rule-versions/" + setId)
                        .header("Authorization", bearer("superadmin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setId.toString()))
                .andExpect(jsonPath("$.jurisdiction").value("IN"))
                .andExpect(jsonPath("$.pfRuleVersion").exists());
    }

    @Test
    void getUnknownRuleVersionSetIs404() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/rule-versions/" + UUID.randomUUID())
                        .header("Authorization", bearer("superadmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/rule-versions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/rule-versions").header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
