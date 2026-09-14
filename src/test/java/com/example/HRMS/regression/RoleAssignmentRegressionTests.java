package com.example.HRMS.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Regression: PUT /users/{userId}/roles behavior — authorization, scope,
 * privilege escalation, validation, and audit recording. Tests the existing
 * documented rules only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleAssignmentRegressionTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID companyA;
    private UUID companyB;
    private UUID targetInA;
    private UUID targetInB;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        companyA = fixtures.insertCompany("Company A");
        companyB = fixtures.insertCompany("Company B");

        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);

        UUID compAdminA = fixtures.createUser("compAadmin", ScopeType.COMPANY, companyA, UserStatus.ACTIVE);
        fixtures.assignRole(compAdminA, RbacTestFixtures.ROLE_COMPANY_ADMIN);

        UUID payAdminA = fixtures.createUser("payadmin", ScopeType.COMPANY, companyA, UserStatus.ACTIVE);
        fixtures.assignRole(payAdminA, RbacTestFixtures.ROLE_PAYROLL_ADMIN);

        targetInA = fixtures.createUser("targetA", ScopeType.COMPANY, companyA, UserStatus.ACTIVE);
        targetInB = fixtures.createUser("targetB", ScopeType.COMPANY, companyB, UserStatus.ACTIVE);
    }

    private String bearer(String username) throws Exception {
        String json = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\""
                                + RbacTestFixtures.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(json).get("accessToken").asString();
    }

    private String body(UUID... roleIds) {
        StringBuilder sb = new StringBuilder("{\"roleIds\":[");
        for (int i = 0; i < roleIds.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(roleIds[i]).append("\"");
        }
        return sb.append("]}").toString();
    }

    @Test
    void companyAdminCanAssignRoleInOwnCompanyAndAuditIsRecorded() throws Exception {
        auditLogRepository.deleteAll();
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_PAYROLL_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes[0]").value("PAYROLL_ADMIN"));

        boolean audited = auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.ROLE_ASSIGNMENT_CHANGE.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome())
                        && a.getEntityId().equals(targetInA));
        assertThat(audited).as("role assignment must emit an audit event for the target user").isTrue();
    }

    @Test
    void invalidRoleIdIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void reservedEmployeeRoleCannotBeAssigned() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_EMPLOYEE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void reservedManagerRoleCannotBeAssigned() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_MANAGER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void companyAdminCannotAssignSuperAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_SUPER_ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void companyAdminCannotAssignRolesToUserInAnotherCompany() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInB + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_PAYROLL_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void payrollAdminLacksUserAdminAndCannotAssignRoles() throws Exception {
        // PAYROLL_ADMIN has payroll.admin + audit.read only, not user.admin.
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .header("Authorization", bearer("payadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_PAYROLL_ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void superAdminCanAssignAssignableRoleInAnyCompany() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInB + "/roles")
                        .header("Authorization", bearer("superadmin"))
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_COMPANY_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes[0]").value("COMPANY_ADMIN"));
    }

    @Test
    void unauthenticatedAssignmentIs401() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + targetInA + "/roles")
                        .contentType("application/json").content(body(RbacTestFixtures.ROLE_PAYROLL_ADMIN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
