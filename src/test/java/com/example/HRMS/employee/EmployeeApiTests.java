package com.example.HRMS.employee;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * V2-001 Employee API tests: create (EMP-001), duplicate Employee ID (EMP-002),
 * invalid employment period (EMP-003), get/list/update, pagination, company
 * isolation (SEC-007), authorization (401/403), audit (AUD-001) and error codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeApiTests {

    private static final String VALID = "{\"employeeId\":\"E001\",\"fullName\":\"Asha Rao\","
            + "\"joiningDate\":\"2026-04-01\",\"employmentType\":\"FULL_TIME\","
            + "\"pan\":\"AAAAA0000A\",\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID scopedCompanyId;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
        // PAYROLL_ADMIN lacks company.admin; its INACTIVE company keeps the
        // single-active-company invariant intact for the 403 test.
        scopedCompanyId = fixtures.insertCompany("Scoped Co", "INACTIVE");
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, scopedCompanyId, UserStatus.ACTIVE);
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

    /** Create the single v0 company + legal entity via the approved API. */
    private void createCompanyAndLegalEntity(String auth) throws Exception {
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"legalName\":\"Acme India Pvt Ltd\",\"countryCode\":\"IN\","
                                + "\"pan\":\"AAAAA0000A\",\"financialYearStart\":\"2025-04-01\"}"))
                .andExpect(status().isCreated());
    }

    // --- EMP-001 ---------------------------------------------------------
    @Test
    void createEmployee201AndAudited() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        auditLogRepository.deleteAll();
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.employeeId").value("E001"))
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.taxRegime").value("NEW_REGIME"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.legalEntityId").exists());

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.EMPLOYEE_CREATED.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome()))).isTrue();
    }

    // --- EMP-002 ---------------------------------------------------------
    @Test
    void duplicateEmployeeIdIs409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // --- EMP-003 ---------------------------------------------------------
    @Test
    void exitBeforeJoiningIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String body = "{\"employeeId\":\"E002\",\"fullName\":\"B\",\"joiningDate\":\"2026-04-10\","
                + "\"exitDate\":\"2026-04-01\",\"employmentType\":\"FULL_TIME\","
                + "\"pan\":\"AAAAA0000A\",\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}";
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void missingRequiredFieldIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Missing fullName.
        String body = "{\"employeeId\":\"E003\",\"joiningDate\":\"2026-04-01\","
                + "\"employmentType\":\"FULL_TIME\",\"pan\":\"AAAAA0000A\","
                + "\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}";
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidPanIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String body = "{\"employeeId\":\"E004\",\"fullName\":\"C\",\"joiningDate\":\"2026-04-01\","
                + "\"employmentType\":\"FULL_TIME\",\"pan\":\"bad\",\"taxRegime\":\"NEW_REGIME\","
                + "\"status\":\"ACTIVE\"}";
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidTaxRegimeEnumIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String body = "{\"employeeId\":\"E005\",\"fullName\":\"D\",\"joiningDate\":\"2026-04-01\","
                + "\"employmentType\":\"FULL_TIME\",\"pan\":\"AAAAA0000A\","
                + "\"taxRegime\":\"MIDDLE\",\"status\":\"ACTIVE\"}";
        mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Get -------------------------------------------------------------
    @Test
    void getEmployee200() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asString();
        mockMvc.perform(get("/api/v1/employees/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("E001"));
    }

    @Test
    void getNonexistentEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- List (paginated, deterministic) ---------------------------------
    @Test
    void listEmployeesIsPaginatedAndOrdered() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        for (String eid : new String[] {"E003", "E001", "E002"}) {
            String body = "{\"employeeId\":\"" + eid + "\",\"fullName\":\"N\","
                    + "\"joiningDate\":\"2026-04-01\",\"employmentType\":\"FULL_TIME\","
                    + "\"pan\":\"AAAAA0000A\",\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}";
            mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                            .contentType("application/json").content(body))
                    .andExpect(status().isCreated());
        }
        JsonNode page = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/employees?page=0&size=2").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn().getResponse().getContentAsString());
        // Deterministic ascending order by employeeId.
        assertThat(page.get("content").get(0).get("employeeId").asString()).isEqualTo("E001");
        assertThat(page.get("content").get(1).get("employeeId").asString()).isEqualTo("E002");
    }

    // --- Update ----------------------------------------------------------
    @Test
    void updateEmployeeSucceedsAndAudits() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asString();
        auditLogRepository.deleteAll();

        String body = "{\"fullName\":\"Asha R\",\"joiningDate\":\"2026-04-01\","
                + "\"employmentType\":\"FULL_TIME\",\"designation\":\"Engineer\","
                + "\"pan\":\"AAAAA0000A\",\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}";
        mockMvc.perform(put("/api/v1/employees/" + id).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha R"))
                .andExpect(jsonPath("$.designation").value("Engineer"))
                .andExpect(jsonPath("$.employeeId").value("E001"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.EMPLOYEE_UPDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void updateNonexistentEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String body = "{\"fullName\":\"X\",\"joiningDate\":\"2026-04-01\","
                + "\"employmentType\":\"FULL_TIME\",\"pan\":\"AAAAA0000A\","
                + "\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}";
        mockMvc.perform(put("/api/v1/employees/" + UUID.randomUUID()).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- SEC-007 company isolation ---------------------------------------
    @Test
    void companyScopedAdminCannotAccessAnotherCompanysEmployee() throws Exception {
        // Super admin creates the single active company (Acme) + legal entity + employee.
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", superAuth)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(created).get("id").asString();

        // A COMPANY_ADMIN belonging to a DIFFERENT company (the scoped INACTIVE co).
        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY, scopedCompanyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);

        // Has company.admin, but not for Acme's company -> 404, no disclosure.
        mockMvc.perform(get("/api/v1/employees/" + employeeId)
                        .header("Authorization", bearer("otheradmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- Authorization ----------------------------------------------------
    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        mockMvc.perform(get("/api/v1/employees").header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
