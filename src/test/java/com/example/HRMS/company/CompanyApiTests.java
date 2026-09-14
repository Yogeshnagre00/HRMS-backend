package com.example.HRMS.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
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
import tools.jackson.databind.ObjectMapper;

/**
 * V0-004 Company API tests: create/get/update, single-active-company (409),
 * validation (400), auth (401/403), audit, response DTO and error contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID companyForScopedActor;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        // Platform admin (company.admin via SUPER_ADMIN) to manage the company.
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
        // A company-scoped PAYROLL_ADMIN (no company.admin) for the 403 case.
        // Its company is INACTIVE so it does not count as the single active v0 company.
        companyForScopedActor = fixtures.insertCompany("Scoped Co", "INACTIVE");
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, companyForScopedActor,
                UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
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
    void superAdminCreatesCompany201() throws Exception {
        auditLogRepository.deleteAll();
        mockMvc.perform(post("/api/v1/company")
                        .header("Authorization", bearer("superadmin"))
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.COMPANY_CREATED.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome()))).isTrue();
    }

    @Test
    void secondCompanyCreationIs409() throws Exception {
        String auth = bearer("superadmin");
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"First\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Second\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidCompanyRequestIs400() throws Exception {
        mockMvc.perform(post("/api/v1/company").header("Authorization", bearer("superadmin"))
                        .contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedCreateIs401() throws Exception {
        mockMvc.perform(post("/api/v1/company")
                        .contentType("application/json").content("{\"name\":\"Acme\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unauthorizedActorWithoutCompanyAdminIs403() throws Exception {
        // PAYROLL_ADMIN lacks company.admin.
        mockMvc.perform(post("/api/v1/company").header("Authorization", bearer("payadmin"))
                        .contentType("application/json").content("{\"name\":\"Acme\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getCompanyReturns200AfterCreate() throws Exception {
        String auth = bearer("superadmin");
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/company").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void getCompanyBeforeCreateIs404() throws Exception {
        mockMvc.perform(get("/api/v1/company").header("Authorization", bearer("superadmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateCompanySucceedsAndAudits() throws Exception {
        String auth = bearer("superadmin");
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated());
        auditLogRepository.deleteAll();
        mockMvc.perform(put("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"Acme Renamed\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Renamed"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.COMPANY_UPDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void updateCompanyInvalidStatusIs400() throws Exception {
        String auth = bearer("superadmin");
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"Acme\",\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
