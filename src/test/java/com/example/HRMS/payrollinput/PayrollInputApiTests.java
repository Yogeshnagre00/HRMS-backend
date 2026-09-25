package com.example.HRMS.payrollinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.payrollinput.repository.ArrearRepository;
import com.example.HRMS.payrollinput.repository.VariableEarningRepository;
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
 * V2-008A.6 Variable Earning + Arrear API tests: VE-001..010, ARR-001..010 plus
 * amount/period/scope/legal-entity/authorization/audit edges and the DRAFT-only
 * lifecycle across every post-DRAFT status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollInputApiTests {

    private static final String[] POST_DRAFT_STATUSES = {
        "CALCULATED", "HEALTH_CHECK", "REVIEW", "APPROVED", "LOCKED", "OUTPUTS"
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private VariableEarningRepository variableEarningRepository;
    @Autowired private ArrearRepository arrearRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private UUID superUserId;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        superUserId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superUserId, RbacTestFixtures.ROLE_SUPER_ADMIN);
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

    private String createEmployee(String auth) throws Exception {
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"employeeId\":\"E001\",\"fullName\":\"Asha Rao\","
                                + "\"joiningDate\":\"2026-01-01\",\"employmentType\":\"FULL_TIME\","
                                + "\"pan\":\"AAAAA0000A\",\"taxRegime\":\"NEW_REGIME\","
                                + "\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asString();
    }

    /** Insert a payroll run directly with the given status; returns its id. */
    private UUID payrollRun(String status) {
        UUID legalEntityId = fixtures.activeLegalEntityId();
        UUID rvs = fixtures.insertRuleVersionSet("VERIFIED");
        return fixtures.insertPayrollRun(legalEntityId, rvs, superUserId, status);
    }

    private MvcResult postEarning(String auth, String empId, UUID runId, String amount)
            throws Exception {
        String body = "{\"payrollRunId\":\"" + runId + "\",\"description\":\"Bonus\","
                + "\"amount\":" + amount + "}";
        return mockMvc.perform(post("/api/v1/employees/" + empId + "/earnings")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andReturn();
    }

    private MvcResult postArrear(String auth, String empId, UUID runId, String amount,
                                 String periodRef) throws Exception {
        String body = "{\"payrollRunId\":\"" + runId + "\",\"amount\":" + amount + ","
                + "\"periodReference\":\"" + periodRef + "\",\"reason\":\"revision\"}";
        return mockMvc.perform(post("/api/v1/employees/" + empId + "/arrears")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andReturn();
    }

    // ---- VE-001 / ARR-001: DRAFT create succeeds --------------------------

    @Test
    void createVariableEarningOnDraftSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        auditLogRepository.deleteAll();
        MvcResult r = postEarning(auth, empId, run, "5000.00");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("employeeId").asString()).isEqualTo(empId);
        assertThat(body.get("payrollRunId").asString()).isEqualTo(run.toString());
        assertThat(body.get("amount").asDouble()).isEqualTo(5000.00);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.VARIABLE_EARNING_CREATED.equals(a.getAction())))
                .isTrue();
    }

    @Test
    void createArrearOnDraftSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        auditLogRepository.deleteAll();
        MvcResult r = postArrear(auth, empId, run, "1200.00", "FY 2025-26");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("periodReference").asString()).isEqualTo("FY 2025-26");
        assertThat(body.get("amount").asDouble()).isEqualTo(1200.00);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.ARREAR_CREATED.equals(a.getAction()))).isTrue();
    }

    // ---- VE-002..007 / ARR-002..007: non-DRAFT → 409 ----------------------

    @Test
    void variableEarningRejectedForEveryPostDraftStatus() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        for (String status : POST_DRAFT_STATUSES) {
            UUID run = payrollRun(status);
            MvcResult r = postEarning(auth, empId, run, "100.00");
            assertThat(r.getResponse().getStatus())
                    .as("variable earning on " + status).isEqualTo(409);
            assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                    .get("code").asString()).isEqualTo("CONFLICT");
        }
        // Zero variable earnings persisted across all rejected attempts.
        assertThat(variableEarningRepository.count()).isZero();
    }

    @Test
    void arrearRejectedForEveryPostDraftStatus() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        for (String status : POST_DRAFT_STATUSES) {
            UUID run = payrollRun(status);
            MvcResult r = postArrear(auth, empId, run, "100.00", "ref");
            assertThat(r.getResponse().getStatus()).as("arrear on " + status).isEqualTo(409);
        }
        assertThat(arrearRepository.count()).isZero();
    }

    // ---- VE-008 / ARR-008: negative amount → 400 --------------------------

    @Test
    void negativeAmountRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        MvcResult ve = postEarning(auth, empId, run, "-1.00");
        assertThat(ve.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(ve.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
        MvcResult arr = postArrear(auth, empId, run, "-1.00", "ref");
        assertThat(arr.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void zeroAmountAccepted() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        assertThat(postEarning(auth, empId, run, "0.00").getResponse().getStatus()).isEqualTo(201);
    }

    // ---- ARR blank period_reference → 400 ---------------------------------

    @Test
    void blankPeriodReferenceRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        String body = "{\"payrollRunId\":\"" + run + "\",\"amount\":100.00,"
                + "\"periodReference\":\"   \",\"reason\":\"x\"}";
        MvcResult r = mockMvc.perform(post("/api/v1/employees/" + empId + "/arrears")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    // ---- VE-009 / ARR-009: multiples allowed ------------------------------

    @Test
    void multipleEarningsAndArrearsPerEmployeeRunAllowed() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        assertThat(postEarning(auth, empId, run, "5000.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(postEarning(auth, empId, run, "2000.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(postEarning(auth, empId, run, "3000.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(postArrear(auth, empId, run, "100.00", "a").getResponse().getStatus()).isEqualTo(201);
        assertThat(postArrear(auth, empId, run, "200.00", "b").getResponse().getStatus()).isEqualTo(201);
        assertThat(variableEarningRepository.count()).isEqualTo(3);
        assertThat(arrearRepository.count()).isEqualTo(2);
    }

    // ---- VE-002/ARR-002 list + ordering -----------------------------------

    @Test
    void listEarningsPaginated() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID run = payrollRun("DRAFT");
        postEarning(auth, empId, run, "5000.00");
        postEarning(auth, empId, run, "2000.00");
        MvcResult r = mockMvc.perform(get("/api/v1/employees/" + empId + "/earnings?page=0&size=10")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("content").size()).isEqualTo(2);
    }

    // ---- legal-entity compatibility / missing run → 409 -------------------

    @Test
    void unknownRunReturns409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        MvcResult r = postEarning(auth, empId, UUID.randomUUID(), "100.00");
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
    }

    // ---- missing employee → 404 -------------------------------------------

    @Test
    void missingEmployeeReturns404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        UUID run = payrollRun("DRAFT");
        MvcResult r = postEarning(auth, UUID.randomUUID().toString(), run, "100.00");
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    // ---- VE-010: no CompensationRecord mutation ---------------------------

    @Test
    void createDoesNotMutateCompensation() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        // Give the employee a compensation record.
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":100000.00,"
                                + "\"basicMonthly\":50000.00,\"hraMonthly\":25000.00,"
                                + "\"daMonthly\":5000.00,"
                                + "\"otherFixedAllowancesMonthly\":25000.00}"))
                .andExpect(status().isCreated());
        UUID run = payrollRun("DRAFT");
        postEarning(auth, empId, run, "5000.00");
        postArrear(auth, empId, run, "1000.00", "ref");
        // Compensation unchanged: still exactly one record with the same CTC.
        mockMvc.perform(get("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ctcMonthly").value(100000.00))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    // ---- authorization + scope --------------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/earnings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        UUID companyId = fixtures.activeCompanyId();
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/arrears")
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void crossCompanyEmployeeIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String empId = createEmployee(superAuth);
        UUID otherCompany = fixtures.insertCompany("Other Co", "INACTIVE");
        UUID otherCa = fixtures.createUser("otherca", ScopeType.COMPANY, otherCompany,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherCa, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/earnings")
                        .header("Authorization", bearer("otherca")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
