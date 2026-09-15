package com.example.HRMS.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.payroll.repository.PayrollRunRepository;
import com.example.HRMS.regression.RbacTestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * V2-007 Payroll Run Foundation API tests: PAYROLL-001..008 plus month/FY
 * boundaries, rule-version validation, employee-population edges, duplicate-run
 * conflict, authorization, company isolation, pagination and audit.
 *
 * <p>Payroll runs use the {@code payroll.admin} permission (PAYROLL_ADMIN), so
 * unlike the other modules the PAYROLL_ADMIN user is the authorized actor here
 * and the company admin without payroll.admin is forbidden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollRunApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private UUID otherCompanyId;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        // Platform super admin provisions company + legal entity + employees.
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
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

    private UUID activeCompanyId() {
        return fixtures.activeCompanyId();
    }

    private String rvsVerified() {
        return fixtures.insertRuleVersionSet("VERIFIED").toString();
    }

    private MvcResult createRun(String auth, String payrollMonth, String ruleVersionSetId)
            throws Exception {
        String body = "{\"payrollMonth\":\"" + payrollMonth + "\",\"ruleVersionSetId\":\""
                + ruleVersionSetId + "\"}";
        return mockMvc.perform(post("/api/v1/payroll/runs").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andReturn();
    }

    // ---- PAYROLL-001 / 006: create + DRAFT -------------------------------

    @Test
    void createPayrollRunSucceedsInDraft() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String rvs = rvsVerified();
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        MvcResult result = createRun(auth, "2026-09-01", rvs);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asString()).isNotBlank();
        assertThat(body.get("payrollMonth").asString()).isEqualTo("2026-09-01");
        assertThat(body.get("status").asString()).isEqualTo("DRAFT");
        assertThat(body.get("ruleVersionSetId").asString()).isEqualTo(rvs);
        assertThat(body.get("parentPayrollRunId").isNull()).isTrue();
        assertThat(body.get("createdBy").asString()).isEqualTo(payId.toString());
        // Lifecycle timestamps null in DRAFT.
        assertThat(body.get("calculatedAt").isNull()).isTrue();
        assertThat(body.get("approvedAt").isNull()).isTrue();
        assertThat(body.get("lockedAt").isNull()).isTrue();
    }

    // ---- PAYROLL-002: month validation -----------------------------------

    @Test
    void nonFirstOfMonthIsRejected() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String rvs = rvsVerified();
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        MvcResult result = createRun(auth, "2026-09-15", rvs);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
    }

    // ---- FY derivation + April boundary ----------------------------------

    @Test
    void financialYearDerivedFromPayrollMonth() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        // FY starts April 1. March 2026 -> 2025-26; April 2026 -> 2026-27.
        JsonNode march = objectMapper.readTree(
                createRun(auth, "2026-03-01", rvsVerified()).getResponse().getContentAsString());
        assertThat(march.get("financialYear").asString()).isEqualTo("2025-26");
        JsonNode april = objectMapper.readTree(
                createRun(auth, "2026-04-01", rvsVerified()).getResponse().getContentAsString());
        assertThat(april.get("financialYear").asString()).isEqualTo("2026-27");
    }

    // ---- PAYROLL-004: automatic employee population -----------------------

    @Test
    void employeePopulationByEmploymentPeriodIntersection() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID legalEntityId = fixtures.activeLegalEntityId();
        // June 2026 run. Boundary employees:
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-JOIN-FIRST", "Joins 1st",
                "2026-06-01", null);            // included (join first day)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-JOIN-LAST", "Joins last",
                "2026-06-30", null);            // included (join last day)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-JOIN-AFTER", "Joins after",
                "2026-07-01", null);            // excluded (joins after month end)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-EXIT-BEFORE", "Exits before",
                "2026-01-01", "2026-05-31");    // excluded (exit before month start)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-EXIT-FIRST", "Exits first",
                "2026-01-01", "2026-06-01");    // included (exit first day)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-EXIT-LAST", "Exits last",
                "2026-01-01", "2026-06-30");    // included (exit last day)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-MID", "Joins+exits mid",
                "2026-06-15", "2026-06-20");    // included (fully inside)
        fixtures.insertEmployeeWithPeriod(legalEntityId, "E-NULL-EXIT", "Null exit",
                "2026-05-01", null);            // included (joined before, no exit)

        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        JsonNode body = objectMapper.readTree(
                createRun(auth, "2026-06-01", rvsVerified()).getResponse().getContentAsString());
        // Included: JOIN-FIRST, JOIN-LAST, EXIT-FIRST, EXIT-LAST, MID, NULL-EXIT = 6.
        assertThat(body.get("eligibleEmployeeCount").asLong()).isEqualTo(6);
    }

    // ---- PAYROLL-005: duplicate primary run -> 409 -----------------------

    @Test
    void duplicatePrimaryRunReturns409() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        assertThat(createRun(auth, "2026-09-01", rvsVerified()).getResponse().getStatus())
                .isEqualTo(201);
        MvcResult dup = createRun(auth, "2026-09-01", rvsVerified());
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(dup.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
        assertThat(payrollRunRepository.count()).isEqualTo(1);
    }

    // ---- rule-version validation -----------------------------------------

    @Test
    void missingRuleVersionSetIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        MvcResult result = createRun(auth, "2026-09-01", UUID.randomUUID().toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void nonVerifiedRuleVersionSetIs409() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");

        String draftRvs = fixtures.insertRuleVersionSet("DRAFT").toString();
        MvcResult result = createRun(auth, "2026-09-01", draftRvs);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
    }

    // ---- PAYROLL-007: authorization --------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void companyAdminWithoutPayrollAdminIs403() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID caId = fixtures.createUser("compadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(caId, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/payroll/runs").header("Authorization", bearer("compadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- PAYROLL-003 / company isolation ---------------------------------

    @Test
    void crossCompanyRunAccessIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");
        String runId = objectMapper.readTree(
                createRun(auth, "2026-09-01", rvsVerified()).getResponse().getContentAsString())
                .get("id").asString();

        // A payroll admin in a DIFFERENT company must not see the run.
        otherCompanyId = fixtures.insertCompany("Other Co", "INACTIVE");
        UUID otherPay = fixtures.createUser("otherpay", ScopeType.COMPANY, otherCompanyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherPay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        mockMvc.perform(get("/api/v1/payroll/runs/" + runId)
                        .header("Authorization", bearer("otherpay")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingRunIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        mockMvc.perform(get("/api/v1/payroll/runs/" + UUID.randomUUID())
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- PAYROLL-008: audit (transactional) ------------------------------

    @Test
    void createRecordsAuditEvent() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");
        auditLogRepository.deleteAll();

        createRun(auth, "2026-09-01", rvsVerified());
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.PAYROLL_RUN_CREATED.equals(a.getAction()))).isTrue();
    }

    // ---- list + pagination + ordering ------------------------------------

    @Test
    void listIsPaginatedAndDeterministic() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String auth = bearer("payadmin");
        createRun(auth, "2026-07-01", rvsVerified());
        createRun(auth, "2026-09-01", rvsVerified());
        createRun(auth, "2026-08-01", rvsVerified());

        MvcResult result = mockMvc.perform(get("/api/v1/payroll/runs?page=0&size=2")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("page").asInt()).isEqualTo(0);
        assertThat(body.get("size").asInt()).isEqualTo(2);
        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("content").size()).isEqualTo(2);
        // Newest payroll month first.
        assertThat(body.get("content").get(0).get("payrollMonth").asString()).isEqualTo("2026-09-01");
        assertThat(body.get("content").get(1).get("payrollMonth").asString()).isEqualTo("2026-08-01");
    }
}
