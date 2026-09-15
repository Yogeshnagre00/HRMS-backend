package com.example.HRMS.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * V2-006A Compensation API tests: COMP-001..012 plus create-conflict, 404s,
 * isolation, authorization, revision atomicity, boundary and precision.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompensationApiTests {

    private static final String VALID =
            "{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":100000.00,"
            + "\"basicMonthly\":50000.00,\"hraMonthly\":25000.00,"
            + "\"otherFixedAllowancesMonthly\":25000.00}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private AuditLogRepository auditLogRepository;

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

    // --- COMP-001 ---------------------------------------------------------
    @Test
    void createFirstCompensation201AndAudited() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        auditLogRepository.deleteAll();
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.employeeId").value(empId))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-04-01"))
                .andExpect(jsonPath("$.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.ctcMonthly").value(100000.00))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.createdBy").exists());
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.COMPENSATION_CREATED.equals(a.getAction()))).isTrue();
    }

    // --- COMP-002 ---------------------------------------------------------
    @Test
    void listCompensation() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/employees/" + empId + "/compensation").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-04-01"))
                .andExpect(jsonPath("$[0].effectiveTo").doesNotExist());
    }

    @Test
    void getByIdReturnsRecord() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String created = mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asString();
        mockMvc.perform(get("/api/v1/compensation/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    // --- COMP-003 / COMP-004 / COMP-011 ----------------------------------
    @Test
    void revisionCreatesNewRecordAndClosesPrior() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String first = mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asString();
        auditLogRepository.deleteAll();

        String revBody = "{\"effectiveFrom\":\"2026-06-01\",\"ctcMonthly\":120000.00,"
                + "\"basicMonthly\":60000.00,\"hraMonthly\":30000.00,"
                + "\"otherFixedAllowancesMonthly\":30000.00,\"reason\":\"annual hike\"}";
        String revised = mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation/revisions")
                        .header("Authorization", auth).contentType("application/json").content(revBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.ctcMonthly").value(120000.00))
                .andReturn().getResponse().getContentAsString();
        String revId = objectMapper.readTree(revised).get("id").asString();
        assertThat(revId).isNotEqualTo(firstId);

        // Prior record preserved and closed at 2026-05-31 (same-day boundary rule).
        mockMvc.perform(get("/api/v1/compensation/" + firstId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom").value("2026-04-01"))
                .andExpect(jsonPath("$.effectiveTo").value("2026-05-31"))
                .andExpect(jsonPath("$.ctcMonthly").value(100000.00));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.COMPENSATION_REVISED.equals(a.getAction()))).isTrue();

        // Two records total, most-recent first.
        mockMvc.perform(get("/api/v1/employees/" + empId + "/compensation").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-06-01"))
                .andExpect(jsonPath("$[1].effectiveFrom").value("2026-04-01"));
    }

    // --- COMP-005 ---------------------------------------------------------
    @Test
    void revisionOnOrBeforeCurrentStartIsConflict() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated());
        // Revision effective on the same day as the current start → overlap → 409.
        String revBody = "{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":120000.00,"
                + "\"basicMonthly\":60000.00,\"hraMonthly\":30000.00,"
                + "\"otherFixedAllowancesMonthly\":30000.00}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation/revisions")
                        .header("Authorization", auth).contentType("application/json").content(revBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void firstCreateWhenCompensationExistsIsConflict() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void revisionWithoutCurrentIsConflict() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation/revisions")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // --- COMP-006 ---------------------------------------------------------
    @Test
    void missingRequiredFieldIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"basicMonthly\":50000.00,"
                + "\"hraMonthly\":25000.00,\"otherFixedAllowancesMonthly\":25000.00}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void negativeMonetaryValueIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":-1.00,"
                + "\"basicMonthly\":50000.00,\"hraMonthly\":25000.00,"
                + "\"otherFixedAllowancesMonthly\":25000.00}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ctcNeedNotEqualComponentSum() throws Exception {
        // CTC is informational: no sum validation. ctc != basic+hra+other is accepted.
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":999999.00,"
                + "\"basicMonthly\":1.00,\"hraMonthly\":1.00,"
                + "\"otherFixedAllowancesMonthly\":1.00}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ctcMonthly").value(999999.00));
    }

    // --- COMP-007 ---------------------------------------------------------
    @Test
    void monetaryPrecisionPreserved() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":100000.55,"
                + "\"basicMonthly\":50000.25,\"hraMonthly\":25000.10,"
                + "\"otherFixedAllowancesMonthly\":24999.20}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ctcMonthly").value(100000.55))
                .andExpect(jsonPath("$.basicMonthly").value(50000.25));
    }

    @Test
    void tooManyDecimalPlacesIs400() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"ctcMonthly\":100000.555,"
                + "\"basicMonthly\":50000.00,\"hraMonthly\":25000.00,"
                + "\"otherFixedAllowancesMonthly\":25000.00}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- COMP-008 ---------------------------------------------------------
    @Test
    void crossCompanyEmployeeCompensationIs404() throws Exception {
        String superAuth = bearer("superadmin");
        String empId = createEmployee(superAuth);
        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY, scopedCompanyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", bearer("otheradmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nonexistentEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createEmployee(auth);
        mockMvc.perform(post("/api/v1/employees/" + UUID.randomUUID() + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nonexistentCompensationIdIs404() throws Exception {
        String auth = bearer("superadmin");
        createEmployee(auth);
        mockMvc.perform(get("/api/v1/compensation/" + UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- COMP-009 ---------------------------------------------------------
    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID() + "/compensation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(get("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- COMP-011 future-dated revision -----------------------------------
    @Test
    void futureEffectiveRevisionAllowed() throws Exception {
        String auth = bearer("superadmin");
        String empId = createEmployee(auth);
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation")
                        .header("Authorization", auth).contentType("application/json").content(VALID))
                .andExpect(status().isCreated());
        String revBody = "{\"effectiveFrom\":\"2027-01-01\",\"ctcMonthly\":150000.00,"
                + "\"basicMonthly\":75000.00,\"hraMonthly\":37500.00,"
                + "\"otherFixedAllowancesMonthly\":37500.00}";
        mockMvc.perform(post("/api/v1/employees/" + empId + "/compensation/revisions")
                        .header("Authorization", auth).contentType("application/json").content(revBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveFrom").value("2027-01-01"));
    }
}
