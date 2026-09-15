package com.example.HRMS.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.attendance.repository.AttendanceExceptionRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * V2-008A.4 Attendance Exception API tests: ATT-001..005 plus quantity/type,
 * employment-period, duplicate, scope, authorization, ordering, audit and
 * delete edges.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceExceptionApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private AttendanceExceptionRepository repository;
    @Autowired private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
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

    /** Employee joining 2026-01-01, exit 2026-12-31. */
    private String createEmployee(String auth, String empBizId) throws Exception {
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"employeeId\":\"" + empBizId + "\",\"fullName\":\"Asha Rao\","
                                + "\"joiningDate\":\"2026-01-01\",\"exitDate\":\"2026-12-31\","
                                + "\"employmentType\":\"FULL_TIME\",\"pan\":\"AAAAA0000A\","
                                + "\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asString();
    }

    private MvcResult postException(String auth, String empId, String date, String type,
                                    String qty) throws Exception {
        String body = "{\"employeeId\":\"" + empId + "\",\"attendanceDate\":\"" + date + "\","
                + "\"exceptionType\":\"" + type + "\",\"quantity\":" + qty + "}";
        return mockMvc.perform(post("/api/v1/attendance/exceptions").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andReturn();
    }

    // ---- ATT-001: create + quantities -------------------------------------

    @Test
    void createFullDayAbsenceSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        auditLogRepository.deleteAll();
        MvcResult r = postException(auth, empId, "2026-06-15", "FULL_DAY_ABSENCE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("exceptionType").asString()).isEqualTo("FULL_DAY_ABSENCE");
        assertThat(body.get("quantity").asDouble()).isEqualTo(1.0);
        assertThat(body.get("createdBy").asString()).isNotBlank();
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.ATTENDANCE_EXCEPTION_CREATED.equals(a.getAction())))
                .isTrue();
    }

    @Test
    void createHalfDayAndLopSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        assertThat(postException(auth, empId, "2026-06-16", "HALF_DAY", "0.5")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(postException(auth, empId, "2026-06-17", "LOP", "1.0")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(postException(auth, empId, "2026-06-18", "LOP", "0.5")
                .getResponse().getStatus()).isEqualTo(201);
    }

    // ---- ATT-002: list ----------------------------------------------------

    @Test
    void listExceptionsPaginatedAndOrdered() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        postException(auth, empId, "2026-06-20", "FULL_DAY_ABSENCE", "1.0");
        postException(auth, empId, "2026-06-10", "HALF_DAY", "0.5");
        MvcResult r = mockMvc.perform(get("/api/v1/attendance/exceptions?employeeId=" + empId
                        + "&page=0&size=10").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("content").get(0).get("attendanceDate").asString()).isEqualTo("2026-06-10");
        assertThat(body.get("content").get(1).get("attendanceDate").asString()).isEqualTo("2026-06-20");
    }

    // ---- ATT-003: update --------------------------------------------------

    @Test
    void updateExceptionSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        String id = objectMapper.readTree(postException(auth, empId, "2026-06-15",
                "FULL_DAY_ABSENCE", "1.0").getResponse().getContentAsString()).get("id").asString();
        String body = "{\"employeeId\":\"" + empId + "\",\"attendanceDate\":\"2026-06-15\","
                + "\"exceptionType\":\"HALF_DAY\",\"quantity\":0.5,\"reason\":\"corrected\"}";
        mockMvc.perform(put("/api/v1/attendance/exceptions/" + id).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionType").value("HALF_DAY"))
                .andExpect(jsonPath("$.quantity").value(0.5))
                .andExpect(jsonPath("$.reason").value("corrected"));
    }

    // ---- ATT-004: delete --------------------------------------------------

    @Test
    void deleteExceptionSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        String id = objectMapper.readTree(postException(auth, empId, "2026-06-15",
                "FULL_DAY_ABSENCE", "1.0").getResponse().getContentAsString()).get("id").asString();
        auditLogRepository.deleteAll();
        mockMvc.perform(delete("/api/v1/attendance/exceptions/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(UUID.fromString(id))).isEmpty();
        // Date is exception-free again: re-create succeeds.
        assertThat(postException(auth, empId, "2026-06-15", "LOP", "1.0")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.ATTENDANCE_EXCEPTION_DELETED.equals(a.getAction())))
                .isTrue();
    }

    // ---- ATT-005: invalid / out-of-employment / duplicate -----------------

    @Test
    void duplicateEmployeeDateReturns409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        assertThat(postException(auth, empId, "2026-06-15", "FULL_DAY_ABSENCE", "1.0")
                .getResponse().getStatus()).isEqualTo(201);
        MvcResult dup = postException(auth, empId, "2026-06-15", "LOP", "1.0");
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(dup.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
    }

    @Test
    void beforeJoiningRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        MvcResult r = postException(auth, empId, "2025-12-31", "FULL_DAY_ABSENCE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void afterExitRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        MvcResult r = postException(auth, empId, "2027-01-01", "FULL_DAY_ABSENCE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void invalidQuantityForTypeRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        // FULL_DAY_ABSENCE with 0.5 → invalid.
        MvcResult r = postException(auth, empId, "2026-06-15", "FULL_DAY_ABSENCE", "0.5");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        // HALF_DAY with 1.0 → invalid.
        assertThat(postException(auth, empId, "2026-06-16", "HALF_DAY", "1.0")
                .getResponse().getStatus()).isEqualTo(400);
        // LOP with 0.75 → invalid.
        assertThat(postException(auth, empId, "2026-06-17", "LOP", "0.75")
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void invalidExceptionTypeRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        String body = "{\"employeeId\":\"" + empId + "\",\"attendanceDate\":\"2026-06-15\","
                + "\"exceptionType\":\"SICK\",\"quantity\":1.0}";
        MvcResult r = mockMvc.perform(post("/api/v1/attendance/exceptions")
                        .header("Authorization", auth).contentType("application/json").content(body))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void missingEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult r = postException(auth, UUID.randomUUID().toString(), "2026-06-15",
                "FULL_DAY_ABSENCE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    // ---- authorization + scope --------------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/exceptions?employeeId=" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        UUID companyId = fixtures.activeCompanyId();
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        mockMvc.perform(get("/api/v1/attendance/exceptions?employeeId=" + empId)
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void crossCompanyExceptionAccessIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String empId = createEmployee(superAuth, "E001");
        String id = objectMapper.readTree(postException(superAuth, empId, "2026-06-15",
                "FULL_DAY_ABSENCE", "1.0").getResponse().getContentAsString()).get("id").asString();
        UUID otherCompany = fixtures.insertCompany("Other Co", "INACTIVE");
        UUID otherCa = fixtures.createUser("otherca", ScopeType.COMPANY, otherCompany,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherCa, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/attendance/exceptions/" + id)
                        .header("Authorization", bearer("otherca")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingExceptionIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(get("/api/v1/attendance/exceptions/" + UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }
}
