package com.example.HRMS.bank;

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
 * V2-002 Bank Details API tests: create/read/update effective bank account,
 * validation (400), employee/bank not found (404), authorization (401/403),
 * cross-company isolation (SEC-007), audit (AUD-001), masked account number and
 * that the full account number never leaks through the API or audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankAccountApiTests {

    private static final String ACCOUNT = "123456789012";
    private static final String VALID = "{\"accountNumber\":\"" + ACCOUNT + "\","
            + "\"ifsc\":\"HDFC0001234\",\"accountHolderName\":\"Asha Rao\"}";

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

    /** Create company + legal entity + one employee via API; return employee id. */
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
    void upsertBankAccountCreates200MaskedAndAudited() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        auditLogRepository.deleteAll();

        String body = mockMvc.perform(put("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ifsc").value("HDFC0001234"))
                .andExpect(jsonPath("$.primary").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountNumberMasked").value("********9012"))
                .andReturn().getResponse().getContentAsString();
        // Full account number must NOT appear anywhere in the response.
        assertThat(body).doesNotContain(ACCOUNT);

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.BANK_ACCOUNT_CREATED.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome()))).isTrue();
        // Sensitive account number must never be stored in audit metadata.
        assertThat(auditLogRepository.findAll().stream()
                .noneMatch(a -> (a.getBeforeSnapshot() != null && a.getBeforeSnapshot().contains(ACCOUNT))
                        || (a.getAfterSnapshot() != null && a.getAfterSnapshot().contains(ACCOUNT))
                        || (a.getReason() != null && a.getReason().contains(ACCOUNT)))).isTrue();
    }

    @Test
    void getBankAccountReturnsMasked200() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/employees/" + empId + "/bank-account").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumberMasked").value("********9012"))
                .andExpect(jsonPath("$.ifsc").value("HDFC0001234"));
    }

    @Test
    void updateBankAccountInPlaceAndAudits() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isOk());
        auditLogRepository.deleteAll();

        mockMvc.perform(put("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"accountNumber\":\"999900001111\",\"ifsc\":\"ICIC0004321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ifsc").value("ICIC0004321"))
                .andExpect(jsonPath("$.accountNumberMasked").value("********1111"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.BANK_ACCOUNT_UPDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void missingRequiredFieldIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        // Missing ifsc.
        mockMvc.perform(put("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"accountNumber\":\"123456789012\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getBankAccountBeforeSetIs404() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/bank-account").header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void bankAccountForNonexistentEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/bank-account")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- SEC-007 cross-company isolation ---------------------------------
    @Test
    void companyScopedAdminCannotAccessAnotherCompanysBankAccount() throws Exception {
        String superAuth = bearer("superadmin");
        String empId = createEmployee(superAuth);
        mockMvc.perform(put("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", superAuth).contentType("application/json").content(VALID))
                .andExpect(status().isOk());

        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY, scopedCompanyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        // Has company.admin but for a different company -> 404, no disclosure.
        mockMvc.perform(get("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", bearer("otheradmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- Authorization ----------------------------------------------------
    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/bank-account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/bank-account")
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
