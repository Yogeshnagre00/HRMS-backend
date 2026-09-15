package com.example.HRMS.workcalendar;

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
import com.example.HRMS.workcalendar.repository.WorkCalendarAssignmentRepository;
import com.example.HRMS.workcalendar.repository.WorkCalendarRepository;
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
 * V2-008A.3 Work Calendar + Employee Assignment API tests: CAL-001..006 plus
 * effective-date/overlap/adjacency, scope, cross-legal-entity, missing
 * employee/calendar, ordering, audit and unsupported-pattern edges.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkCalendarApiTests {

    private static final String CAL_5DAY =
            "{\"name\":\"Standard 5-day\",\"effectiveFrom\":\"2026-01-01\","
            + "\"effectiveTo\":null,\"mondayToFriday\":true,"
            + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private WorkCalendarRepository calendarRepository;
    @Autowired private WorkCalendarAssignmentRepository assignmentRepository;
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

    private String createCalendar(String auth, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    // ---- CAL-001: create --------------------------------------------------

    @Test
    void createStandardCalendarSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        auditLogRepository.deleteAll();
        MvcResult r = mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json").content(CAL_5DAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Standard 5-day"))
                .andExpect(jsonPath("$.mondayToFriday").value(true))
                .andExpect(jsonPath("$.saturdaySundayWeeklyOff").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.WORK_CALENDAR_CREATED.equals(a.getAction()))).isTrue();
    }

    // ---- CAL-002: list ----------------------------------------------------

    @Test
    void listCalendarsPaginatedAndOrdered() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        createCalendar(auth, "{\"name\":\"C1\",\"effectiveFrom\":\"2026-01-01\","
                + "\"effectiveTo\":\"2026-06-30\",\"mondayToFriday\":true,"
                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}");
        createCalendar(auth, "{\"name\":\"C2\",\"effectiveFrom\":\"2026-07-01\","
                + "\"effectiveTo\":null,\"mondayToFriday\":true,"
                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}");
        MvcResult r = mockMvc.perform(get("/api/v1/work-calendars?page=0&size=10")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("content").get(0).get("effectiveFrom").asString()).isEqualTo("2026-01-01");
        assertThat(body.get("content").get(1).get("effectiveFrom").asString()).isEqualTo("2026-07-01");
    }

    // ---- CAL-003: update --------------------------------------------------

    @Test
    void updateCalendarSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String id = objectMapper.readTree(createCalendar(auth, CAL_5DAY)).get("id").asString();
        mockMvc.perform(put("/api/v1/work-calendars/" + id).header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed\",\"effectiveFrom\":\"2026-01-01\","
                                + "\"effectiveTo\":null,\"mondayToFriday\":true,"
                                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ---- CAL-004: overlap rejected + adjacency valid ----------------------

    @Test
    void overlappingCalendarRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        createCalendar(auth, "{\"name\":\"C1\",\"effectiveFrom\":\"2026-01-01\","
                + "\"effectiveTo\":\"2026-07-01\",\"mondayToFriday\":true,"
                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}");
        // Shared boundary date 2026-07-01 → overlap → 409.
        MvcResult r = mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"C2\",\"effectiveFrom\":\"2026-07-01\","
                                + "\"effectiveTo\":null,\"mondayToFriday\":true,"
                                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}"))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
    }

    @Test
    void adjacentCalendarPeriodsValid() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        createCalendar(auth, "{\"name\":\"C1\",\"effectiveFrom\":\"2026-01-01\","
                + "\"effectiveTo\":\"2026-06-30\",\"mondayToFriday\":true,"
                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}");
        // C2 starts the day after C1 ends → no overlap → 201.
        mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"C2\",\"effectiveFrom\":\"2026-07-01\","
                                + "\"effectiveTo\":null,\"mondayToFriday\":true,"
                                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated());
    }

    // ---- CAL-005: assign to employee --------------------------------------

    @Test
    void assignCalendarToEmployeeSucceeds() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        String calId = objectMapper.readTree(createCalendar(auth, CAL_5DAY)).get("id").asString();
        auditLogRepository.deleteAll();
        mockMvc.perform(put("/api/v1/employees/" + empId + "/work-calendar")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"workCalendarId\":\"" + calId + "\","
                                + "\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(empId))
                .andExpect(jsonPath("$.workCalendarId").value(calId))
                .andExpect(jsonPath("$.createdBy").exists());
        // Assignment visible via calendar assignments endpoint.
        mockMvc.perform(get("/api/v1/work-calendars/" + calId + "/assignments")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(empId));
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.WORK_CALENDAR_ASSIGNED.equals(a.getAction()))).isTrue();
    }

    @Test
    void overlappingEmployeeAssignmentRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        String calId = objectMapper.readTree(createCalendar(auth, CAL_5DAY)).get("id").asString();
        mockMvc.perform(put("/api/v1/employees/" + empId + "/work-calendar")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"workCalendarId\":\"" + calId + "\","
                                + "\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":\"2026-06-30\"}"))
                .andExpect(status().isOk());
        // Shared boundary 2026-06-30 → overlap → 409.
        mockMvc.perform(put("/api/v1/employees/" + empId + "/work-calendar")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"workCalendarId\":\"" + calId + "\","
                                + "\"effectiveFrom\":\"2026-06-30\",\"effectiveTo\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ---- CAL-006: authorization -------------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/work-calendars"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        UUID companyId = fixtures.activeCompanyId();
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        mockMvc.perform(get("/api/v1/work-calendars").header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void companyAdminAllowed() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        UUID companyId = fixtures.activeCompanyId();
        UUID ca = fixtures.createUser("compadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(ca, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/work-calendars").header("Authorization", bearer("compadmin")))
                .andExpect(status().isOk());
    }

    // ---- cross-company / cross-legal-entity / missing ---------------------

    @Test
    void crossCompanyCalendarAccessIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String calId = objectMapper.readTree(createCalendar(superAuth, CAL_5DAY)).get("id").asString();
        UUID otherCompany = fixtures.insertCompany("Other Co", "INACTIVE");
        UUID otherCa = fixtures.createUser("otherca", ScopeType.COMPANY, otherCompany,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherCa, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/work-calendars/" + calId)
                        .header("Authorization", bearer("otherca")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingCalendarIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(get("/api/v1/work-calendars/" + UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignMissingEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String calId = objectMapper.readTree(createCalendar(auth, CAL_5DAY)).get("id").asString();
        mockMvc.perform(put("/api/v1/employees/" + UUID.randomUUID() + "/work-calendar")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"workCalendarId\":\"" + calId + "\","
                                + "\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignMissingCalendarIs409WrongEntity() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        // Random calendar id is not in the employee's legal entity → 409.
        mockMvc.perform(put("/api/v1/employees/" + empId + "/work-calendar")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"workCalendarId\":\"" + UUID.randomUUID() + "\","
                                + "\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ---- validation edges -------------------------------------------------

    @Test
    void unsupportedPatternRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult r = mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"6-day\",\"effectiveFrom\":\"2026-01-01\","
                                + "\"effectiveTo\":null,\"mondayToFriday\":true,"
                                + "\"saturdaySundayWeeklyOff\":false,\"status\":\"ACTIVE\"}"))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void invalidEffectiveRangeRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult r = mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"Bad\",\"effectiveFrom\":\"2026-07-01\","
                                + "\"effectiveTo\":\"2026-01-01\",\"mondayToFriday\":true,"
                                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ACTIVE\"}"))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void invalidStatusRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult r = mockMvc.perform(post("/api/v1/work-calendars").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"Bad\",\"effectiveFrom\":\"2026-01-01\","
                                + "\"effectiveTo\":null,\"mondayToFriday\":true,"
                                + "\"saturdaySundayWeeklyOff\":true,\"status\":\"ARCHIVED\"}"))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void assignmentPersistsWithFkIntegrity() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth);
        String calId = objectMapper.readTree(createCalendar(auth, CAL_5DAY)).get("id").asString();
        mockMvc.perform(put("/api/v1/employees/" + empId + "/work-calendar")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"workCalendarId\":\"" + calId + "\","
                                + "\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null}"))
                .andExpect(status().isOk());
        assertThat(assignmentRepository
                .findByEmployeeIdOrderByEffectiveFromAsc(UUID.fromString(empId))).hasSize(1);
        assertThat(calendarRepository.findById(UUID.fromString(calId))).isPresent();
    }
}
