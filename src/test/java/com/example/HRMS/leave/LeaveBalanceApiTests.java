package com.example.HRMS.leave;

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
 * V2-004 Leave Balance API tests: GET existing/missing, PUT create/update,
 * server-derived FY (client cannot control), YYYY-YY format, uniqueness,
 * PAID_LEAVE treatment, available-balance formula (incl. surfaced negative),
 * validation, employee 404, cross-company isolation, authorization, audit, DTO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveBalanceApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID scopedCompanyId;

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
    void putCreatesBalanceWithFormulaFyTreatmentAndAudit() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        auditLogRepository.deleteAll();

        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":12.00,\"approvedAdditions\":3.00,"
                                + "\"usedQuantity\":5.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.leaveTreatment").value("PAID_LEAVE"))
                .andExpect(jsonPath("$.financialYear").value(expectedCurrentFy()))
                .andExpect(jsonPath("$.openingBalance").value(12.00))
                .andExpect(jsonPath("$.approvedAdditions").value(3.00))
                .andExpect(jsonPath("$.usedQuantity").value(5.00))
                .andExpect(jsonPath("$.availableBalance").value(10.00));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.LEAVE_BALANCE_CREATED.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome()))).isTrue();
    }

    @Test
    void financialYearMatchesYyyyDashYy() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":0,\"approvedAdditions\":0,\"usedQuantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear",
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}")));
    }

    @Test
    void insufficientBalanceIsSurfacedAsNegativeNotCapped() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        // used > opening + additions -> negative available, surfaced (not capped, not LOP).
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":2.00,\"approvedAdditions\":1.00,"
                                + "\"usedQuantity\":5.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(-2.00));
    }

    @Test
    void getReturnsBalance() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":10,\"approvedAdditions\":0,\"usedQuantity\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(10))
                .andExpect(jsonPath("$.leaveTreatment").value("PAID_LEAVE"));
    }

    @Test
    void getMissingBalanceIs404() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void putUpdatesExistingWithoutDuplicateAndAudits() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String first = mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":10,\"approvedAdditions\":0,\"usedQuantity\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asString();
        auditLogRepository.deleteAll();

        String second = mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":15,\"approvedAdditions\":2,\"usedQuantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(13))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(second).get("id").asString()).isEqualTo(firstId);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.LEAVE_BALANCE_UPDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void clientCannotControlFinancialYearOrTreatment() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":1,\"approvedAdditions\":0,\"usedQuantity\":0,"
                                + "\"financialYear\":\"1999-00\",\"leaveTreatment\":\"SICK\","
                                + "\"availableBalance\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value(expectedCurrentFy()))
                .andExpect(jsonPath("$.leaveTreatment").value("PAID_LEAVE"))
                .andExpect(jsonPath("$.availableBalance").value(1));
    }

    @Test
    void missingRequiredFieldIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":10,\"usedQuantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void negativeInputIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":-1,\"approvedAdditions\":0,\"usedQuantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonexistentEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/leave-balance")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void companyScopedAdminCannotAccessAnotherCompanysBalance() throws Exception {
        String superAuth = bearer("superadmin");
        String empId = createEmployee(superAuth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", superAuth).contentType("application/json")
                        .content("{\"openingBalance\":1,\"approvedAdditions\":0,\"usedQuantity\":0}"))
                .andExpect(status().isOk());

        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY, scopedCompanyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", bearer("otheradmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/leave-balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
