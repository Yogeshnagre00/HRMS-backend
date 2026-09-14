package com.example.HRMS.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.regression.RbacTestFixtures;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * V2-003 Opening Tax State API tests: GET existing/missing, PATCH create/update,
 * server-derived FY (client cannot control), YYYY-YY format, uniqueness, invalid
 * monetary, employee 404, cross-company isolation, authorization, audit, and
 * that the DTO is returned (not the entity).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpeningTaxStateApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID scopedCompanyId;

    /** Expected current FY under the April-1 rule and Asia/Kolkata business date. */
    private static String expectedCurrentFy() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate aprilFirst = LocalDate.of(today.getYear(), 4, 1);
        int startYear = today.isBefore(aprilFirst) ? today.getYear() - 1 : today.getYear();
        return String.format("%04d-%02d", startYear, (startYear + 1) % 100);
    }

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
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

    /** Create company + legal entity (FY start Apr 1) + one employee; return employee id. */
    private String createEmployee(String auth) throws Exception {
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"legalName\":\"Acme India Pvt Ltd\",\"countryCode\":\"IN\","
                                + "\"pan\":\"AAAAA0000A\",\"financialYearStart\":\"2025-04-01\"}"))
                .andExpect(status().isCreated());
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"employeeId\":\"E001\",\"fullName\":\"Asha Rao\","
                                + "\"joiningDate\":\"2026-04-01\",\"employmentType\":\"FULL_TIME\","
                                + "\"pan\":\"AAAAA0000A\",\"taxRegime\":\"NEW_REGIME\","
                                + "\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asString();
    }

    @Test
    void patchCreatesOpeningStateWithServerDerivedFyAndAudits() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        auditLogRepository.deleteAll();

        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":500000.00,"
                                + "\"tdsAlreadyDeducted\":25000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.employeeId").value(empId))
                .andExpect(jsonPath("$.financialYear").value(expectedCurrentFy()))
                .andExpect(jsonPath("$.cumulativeTaxableIncome").value(500000.00))
                .andExpect(jsonPath("$.tdsAlreadyDeducted").value(25000.00))
                .andExpect(jsonPath("$.source").value("MANUAL"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.OPENING_TAX_STATE_CREATED.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome()))).isTrue();
    }

    @Test
    void financialYearMatchesYyyyDashYy() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":1,\"tdsAlreadyDeducted\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear", org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}")));
    }

    @Test
    void getReturnsOpeningState() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":700000,\"tdsAlreadyDeducted\":40000}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulativeTaxableIncome").value(700000))
                .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    void getMissingOpeningStateIs404() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void patchPartiallyUpdatesPreservingOmittedField() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":500000,\"tdsAlreadyDeducted\":25000}"))
                .andExpect(status().isOk());
        // Update only taxable income; TDS-already-deducted must be preserved.
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":650000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulativeTaxableIncome").value(650000))
                .andExpect(jsonPath("$.tdsAlreadyDeducted").value(25000));
        // Update only TDS; taxable income must be preserved.
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"tdsAlreadyDeducted\":30000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulativeTaxableIncome").value(650000))
                .andExpect(jsonPath("$.tdsAlreadyDeducted").value(30000));
    }

    @Test
    void patchUpdateDoesNotCreateDuplicateAndAudits() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String first = mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":1000,\"tdsAlreadyDeducted\":10}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asString();
        auditLogRepository.deleteAll();

        String second = mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":2000}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Same record (no duplicate created for same employee + FY).
        assertThat(objectMapper.readTree(second).get("id").asString()).isEqualTo(firstId);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.OPENING_TAX_STATE_UPDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void clientCannotControlFinancialYear() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        // Client attempts to inject a financialYear; it must be ignored, server derives it.
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":100,\"tdsAlreadyDeducted\":10,"
                                + "\"financialYear\":\"1999-00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value(expectedCurrentFy()));
    }

    @Test
    void createWithoutBothFieldsIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        // No existing record; supplying only one field cannot establish a complete state.
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void negativeMonetaryValueIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":-5,\"tdsAlreadyDeducted\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonexistentEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/opening-tax-state")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void companyScopedAdminCannotAccessAnotherCompanysOpeningState() throws Exception {
        String superAuth = bearer("superadmin");
        String empId = createEmployee(superAuth);
        mockMvc.perform(patch("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", superAuth).contentType("application/json")
                        .content("{\"cumulativeTaxableIncome\":100,\"tdsAlreadyDeducted\":10}"))
                .andExpect(status().isOk());

        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY, scopedCompanyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", bearer("otheradmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/opening-tax-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/opening-tax-state")
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
