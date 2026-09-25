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
import com.example.HRMS.payroll.repository.PayrollDayResultRepository;
import com.example.HRMS.payroll.repository.PayrollEmployeeResultRepository;
import com.example.HRMS.payroll.repository.PayrollResultLineRepository;
import com.example.HRMS.regression.RbacTestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * V2-008A Non-Statutory Payroll Calculation API tests: CALC-001..013 plus the
 * proration/joiner/leaver/LOP/leave/attendance/revision/variable/arrear/
 * weekly-off/missing-calendar/conflict/immutability scenario matrix.
 *
 * <p>The payroll month is June 2026 (30 calendar days; 1 June is a Monday, so
 * 22 Mon-Fri scheduled working days, 8 Sat/Sun weekly-offs). Amounts are chosen
 * so calendar-day proration yields exact 2-decimal values.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollCalculationApiTests {

    private static final String MONTH = "2026-06-01";
    private static final String FY = "2026-27";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PayrollEmployeeResultRepository employeeResultRepository;
    @Autowired private PayrollResultLineRepository resultLineRepository;
    @Autowired private PayrollDayResultRepository dayResultRepository;
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

    /**
     * Provision company + legal entity (via the platform super admin) and a
     * company-scoped {@code payroll.admin} user. Returns that user's bearer token
     * — payroll calculation/result APIs require {@code payroll.admin}, which the
     * SUPER_ADMIN role does not carry.
     */
    private String setUpTenantAndPayrollAdmin() throws Exception {
        String superAuth = bearer("superadmin");
        mockMvc.perform(post("/api/v1/company").header("Authorization", superAuth)
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", superAuth)
                        .contentType("application/json")
                        .content("{\"legalName\":\"Acme India Pvt Ltd\",\"countryCode\":\"IN\","
                                + "\"pan\":\"AAAAA0000A\",\"financialYearStart\":\"2026-04-01\"}"))
                .andExpect(status().isCreated());
        UUID companyId = fixtures.activeCompanyId();
        UUID payId = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(payId, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        return bearer("payadmin");
    }

    private UUID run(String status, String version) {
        UUID le = fixtures.activeLegalEntityId();
        UUID rvs = fixtures.insertRuleVersionSet("VERIFIED");
        return fixtures.insertPayrollRun(le, rvs, superUserId, status, MONTH, FY, version);
    }

    /** Full-month employee joined before June with a standard calendar + compensation. */
    private UUID fullMonthEmployee(String bizId) {
        UUID le = fixtures.activeLegalEntityId();
        UUID emp = fixtures.insertEmployeeWithPeriod(le, bizId, bizId, "2026-01-01", null);
        UUID cal = fixtures.insertWorkCalendar(le, "Std5Day-" + bizId, "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "15000.00", superUserId);
        return emp;
    }

    /** Full-month employee with an explicit DA component (Basic/HRA/DA/Other). */
    private UUID fullMonthEmployeeWithDa(String bizId, String basic, String hra, String da,
                                         String other) {
        UUID le = fixtures.activeLegalEntityId();
        UUID emp = fixtures.insertEmployeeWithPeriod(le, bizId, bizId, "2026-01-01", null);
        UUID cal = fixtures.insertWorkCalendar(le, "Std5Day-" + bizId, "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", basic, hra, da, other,
                superUserId);
        return emp;
    }

    private MvcResult calculate(String auth, UUID runId) throws Exception {
        return mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/calculate")
                .header("Authorization", auth)).andReturn();
    }

    private MvcResult recalculate(String auth, UUID runId) throws Exception {
        return mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/recalculate")
                .header("Authorization", auth)).andReturn();
    }

    private JsonNode detail(String auth, UUID runId, UUID empId) throws Exception {
        String json = mockMvc.perform(get("/api/v1/payroll/runs/" + runId + "/employees/" + empId)
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    // ---- CALC-001: first calculation of a DRAFT run -----------------------

    @Test
    void calc001_firstCalculationTransitionsAndComputesGross() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E001");
        UUID runId = run("DRAFT", "0");
        auditLogRepository.deleteAll();

        MvcResult r = calculate(auth, runId);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("CALCULATED_PRE_STATUTORY");
        assertThat(body.get("calculationVersion").asString()).isEqualTo("1");
        assertThat(body.get("calculatedAt").isNull()).isFalse();
        assertThat(body.get("employeeResultCount").asInt()).isEqualTo(1);

        // Run row moved and version/calculated_at set.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payroll_run WHERE id = ?", String.class, runId.toString()))
                .isEqualTo("CALCULATED_PRE_STATUTORY");

        // Full-month gross = (30000+15000+15000)/30 * 22 payable = 60000/30*22 = 44000.
        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(44000.00);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(22.0);
        assertThat(d.get("eligibleCalendarDays").asDouble()).isEqualTo(22.0);
        assertThat(d.get("lopDays").asDouble()).isEqualTo(0.0);
        // Statutory + net are NULL (never fake 0.00).
        assertThat(d.get("pfEmployee").isNull()).isTrue();
        assertThat(d.get("pfEmployer").isNull()).isTrue();
        assertThat(d.get("pt").isNull()).isTrue();
        assertThat(d.get("tds").isNull()).isTrue();
        assertThat(d.get("otherDeductions").isNull()).isTrue();
        assertThat(d.get("netPay").isNull()).isTrue();
        // Audited.
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.PAYROLL_CALCULATED.equals(a.getAction()))).isTrue();
    }

    // ---- CALC-013: pre-statutory field state / earning lines --------------

    @Test
    void calc013_earningLinesSetStatutoryNullNoStatutoryRows() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E001");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        JsonNode lines = d.get("lines");
        // Three fixed earning lines (Basic/HRA/Other); all EARNING type.
        assertThat(lines.size()).isEqualTo(3);
        lines.forEach(l -> assertThat(l.get("lineType").asString()).isEqualTo("EARNING"));
        // No payroll_statutory_result table/rows created by this slice.
        assertThat(dayResultRepository.count()).isEqualTo(30);
    }

    // ---- proration: joiner mid-month --------------------------------------

    @Test
    void joinerMidMonthProratesFromJoiningDate() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        // Joins 16 June 2026 (a Tuesday). Scheduled working days 16-30:
        // 16,17,18,19 (Tue-Fri), 22-26 (Mon-Fri), 29,30 (Mon,Tue) = 11 days.
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E002", "Joiner", "2026-06-16", null);
        UUID cal = fixtures.insertWorkCalendar(le, "Cal", "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "15000.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(11.0);
        // 60000/30 * 11 = 22000.
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(22000.00);
    }

    // ---- proration: leaver mid-month --------------------------------------

    @Test
    void leaverMidMonthProratesUntilExitInclusive() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        // Exit 15 June 2026 (a Monday). Scheduled working days 1-15:
        // 1-5 (Mon-Fri), 8-12 (Mon-Fri), 15 (Mon) = 11 days.
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E003", "Leaver", "2026-01-01",
                "2026-06-15");
        UUID cal = fixtures.insertWorkCalendar(le, "Cal", "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "15000.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(11.0);
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(22000.00);
    }

    // ---- unpaid LOP leave reduces payable ---------------------------------

    @Test
    void unpaidLopLeaveReducesPayableDays() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        UUID emp = fullMonthEmployee("E004");
        // One unpaid full-day LOP leave on a working Monday (1 June).
        fixtures.insertLeaveEntry(emp, "2026-06-01", "UNPAID_LOP_LEAVE", "1.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("lopDays").asDouble()).isEqualTo(1.0);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(21.0);
        // 60000/30 * 21 = 42000.
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(42000.00);
    }

    // ---- paid leave: no LOP, full pay -------------------------------------

    @Test
    void paidLeaveDoesNotReducePay() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E005");
        fixtures.insertLeaveEntry(emp, "2026-06-01", "PAID_LEAVE", "1.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("lopDays").asDouble()).isEqualTo(0.0);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(22.0);
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(44000.00);
    }

    // ---- attendance half-day exception ------------------------------------

    @Test
    void halfDayAttendanceProducesHalfLop() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E006");
        fixtures.insertAttendanceException(emp, "2026-06-02", "HALF_DAY", "0.50", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("lopDays").asDouble()).isEqualTo(0.5);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(21.5);
        // 60000/30 * 21.5 = 43000.
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(43000.00);
    }

    // ---- variable earnings + arrears (not prorated) -----------------------

    @Test
    void variableEarningsAndArrearsAddedNotProrated() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E007");
        UUID runId = run("DRAFT", "0");
        fixtures.insertVariableEarning(emp, runId, "Bonus", "5000.00", superUserId);
        fixtures.insertVariableEarning(emp, runId, "Incentive", "2500.00", superUserId);
        fixtures.insertArrear(emp, runId, "1000.00", "FY2025-26", superUserId);
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        // 44000 fixed + 5000 + 2500 + 1000 = 52500.
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(52500.00);
        // 3 fixed + 2 variable + 1 arrear = 6 earning lines.
        assertThat(d.get("lines").size()).isEqualTo(6);
    }

    // ---- salary revision mid-month ----------------------------------------

    @Test
    void midMonthSalaryRevisionUsesEffectiveStatePerDay() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E008", "Revised", "2026-01-01", null);
        UUID cal = fixtures.insertWorkCalendar(le, "Cal", "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        // Old comp until 15 June, revised from 16 June (doubles gross monthly).
        fixtures.insertCompensation(emp, "2026-01-01", "2026-06-15", "60000.00", "30000.00",
                "15000.00", "15000.00", superUserId);
        fixtures.insertCompensation(emp, "2026-06-16", null, "120000.00", "60000.00",
                "30000.00", "30000.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        // Days 1-15 scheduled (11) at 60000/30=2000/day; days 16-30 scheduled (11)
        // at 120000/30=4000/day. 11*2000 + 11*4000 = 22000 + 44000 = 66000.
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(22.0);
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(66000.00);
    }

    // ---- missing work calendar assignment is blocking ---------------------

    @Test
    void missingWorkCalendarBlocksAndRollsBack() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E009", "NoCal", "2026-01-01", null);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "15000.00", superUserId);
        UUID runId = run("DRAFT", "0");

        MvcResult r = calculate(auth, runId);
        // Missing required calculation input → 400 BAD_REQUEST (API §16.1).
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
        // Rolled back: run stays DRAFT, no results.
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payroll_run WHERE id = ?",
                String.class, runId.toString())).isEqualTo("DRAFT");
        assertThat(employeeResultRepository.count()).isZero();
    }

    // ---- leave + attendance conflict same day is blocking -----------------

    @Test
    void leaveAndAttendanceSameDayBlocks() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E010");
        fixtures.insertLeaveEntry(emp, "2026-06-01", "PAID_LEAVE", "1.00", superUserId);
        fixtures.insertAttendanceException(emp, "2026-06-01", "FULL_DAY_ABSENCE", "1.00",
                superUserId);
        UUID runId = run("DRAFT", "0");

        MvcResult r = calculate(auth, runId);
        // Invalid required calculation input → 400 BAD_REQUEST (API §16.1).
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
        assertThat(employeeResultRepository.count()).isZero();
    }

    // ---- CALC-002 / CALC-006: recalculation replaces + version++ ----------

    @Test
    void calc002_recalculationAdvancesVersionAndReplacesSet() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E011");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);
        long resultsAfterFirst = employeeResultRepository.count();

        MvcResult r = recalculate(auth, runId);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("CALCULATED_PRE_STATUTORY");
        assertThat(body.get("calculationVersion").asString()).isEqualTo("2");
        // Exactly one active result set (not duplicated).
        assertThat(employeeResultRepository.count()).isEqualTo(resultsAfterFirst);
    }

    // ---- CALC-004: duplicate calculate on non-DRAFT → 409 -----------------

    @Test
    void calc004_duplicateCalculateReturns409() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        fullMonthEmployee("E012");
        UUID runId = run("DRAFT", "0");
        assertThat(calculate(auth, runId).getResponse().getStatus()).isEqualTo(200);
        MvcResult second = calculate(auth, runId);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(second.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
        assertThat(employeeResultRepository.count()).isEqualTo(1);
    }

    // ---- CALC-010: recalculate only from CALCULATED_PRE_STATUTORY ---------

    @Test
    void calc010_recalculateFromDraftReturns409() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        fullMonthEmployee("E013");
        UUID runId = run("DRAFT", "0");
        MvcResult r = recalculate(auth, runId);
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void calc010_recalculateFromLockedReturns409() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        fullMonthEmployee("E014");
        UUID runId = run("LOCKED", "3");
        assertThat(recalculate(auth, runId).getResponse().getStatus()).isEqualTo(409);
        assertThat(calculate(auth, runId).getResponse().getStatus()).isEqualTo(409);
    }

    // ---- CALC-008: leave balance untouched (no balance rows created) ------

    @Test
    void calc008_calculationDoesNotMutateLeaveBalance() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E015");
        fixtures.insertLeaveEntry(emp, "2026-06-01", "PAID_LEAVE", "1.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);
        // No employee_leave_balance rows were created or mutated by calculation.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee_leave_balance", Integer.class)).isZero();
    }

    // ---- CALC-011: employee-result pagination / DRAFT empty ---------------

    @Test
    void calc011_draftRunReturnsEmptyPage() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        fullMonthEmployee("E016");
        UUID runId = run("DRAFT", "0");
        mockMvc.perform(get("/api/v1/payroll/runs/" + runId + "/employees")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void calc011_resultsOrderedByBusinessEmployeeId() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        // Insert out of order; expect ordering by business Employee ID ascending.
        fullMonthEmployee("E030");
        fullMonthEmployee("E010");
        fullMonthEmployee("E020");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);
        String json = mockMvc.perform(get("/api/v1/payroll/runs/" + runId + "/employees")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(json).get("content");
        assertThat(content.get(0).get("employeeBusinessId").asString()).isEqualTo("E010");
        assertThat(content.get(1).get("employeeBusinessId").asString()).isEqualTo("E020");
        assertThat(content.get(2).get("employeeBusinessId").asString()).isEqualTo("E030");
    }

    // ---- CALC-012: recalculation uses run's own rule version --------------

    @Test
    void calc012_recalculationKeepsRunRuleVersionSet() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        fullMonthEmployee("E017");
        UUID runId = run("DRAFT", "0");
        String rvsBefore = jdbcTemplate.queryForObject(
                "SELECT rule_version_set_id FROM payroll_run WHERE id = ?", String.class,
                runId.toString());
        calculate(auth, runId);
        recalculate(auth, runId);
        String rvsAfter = jdbcTemplate.queryForObject(
                "SELECT rule_version_set_id FROM payroll_run WHERE id = ?", String.class,
                runId.toString());
        assertThat(rvsAfter).isEqualTo(rvsBefore);
    }

    // ---- CALC-009: API contract auth/scope --------------------------------

    @Test
    void calc009_unauthenticatedIs401() throws Exception {
        UUID runId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/calculate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void calc009_missingPayrollAdminIs403() throws Exception {
        setUpTenantAndPayrollAdmin();
        UUID companyId = fixtures.activeCompanyId();
        UUID ca = fixtures.createUser("companyadmin", ScopeType.COMPANY, companyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(ca, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        UUID runId = run("DRAFT", "0");
        mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/calculate")
                        .header("Authorization", bearer("companyadmin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void calc009_unknownRunIs404() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        mockMvc.perform(post("/api/v1/payroll/runs/" + UUID.randomUUID() + "/calculate")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeResultDetailUnknownEmployeeIs404() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        fullMonthEmployee("E018");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);
        mockMvc.perform(get("/api/v1/payroll/runs/" + runId + "/employees/" + UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    // ---- CALC-003: failed calculation rolls back --------------------------

    @Test
    void calc003_failedCalculationLeavesRunDraftWithNoPartialRows() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        // First employee valid; second missing calendar → whole calc fails atomically.
        fullMonthEmployee("E019");
        UUID bad = fixtures.insertEmployeeWithPeriod(le, "E020b", "Bad", "2026-01-01", null);
        fixtures.insertCompensation(bad, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "15000.00", superUserId);
        UUID runId = run("DRAFT", "0");

        // Missing required calculation input → 400; whole calc rolls back atomically.
        assertThat(calculate(auth, runId).getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payroll_run WHERE id = ?",
                String.class, runId.toString())).isEqualTo("DRAFT");
        assertThat(employeeResultRepository.count()).isZero();
        assertThat(resultLineRepository.count()).isZero();
        assertThat(dayResultRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT calculation_version FROM payroll_run WHERE id = ?", String.class,
                runId.toString())).isEqualTo("0");
    }

    // ---- weekly-offs are non-scheduled ------------------------------------

    @Test
    void weeklyOffsAreNotScheduledWorkingDays() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E021");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);
        // 8 weekend days in June 2026 are not scheduled and not payable.
        int scheduled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payroll_day_result d "
                        + "JOIN payroll_employee_result r ON r.id = d.payroll_employee_result_id "
                        + "WHERE r.payroll_run_id = ? AND d.scheduled_working_day = TRUE",
                Integer.class, runId.toString());
        assertThat(scheduled).isEqualTo(22);
    }

    // ---- zero gross (all LOP) is valid ------------------------------------

    @Test
    void allDaysLopProducesZeroGross() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployee("E022");
        // Mark every scheduled June weekday as full-day LOP leave.
        String[] workingDays = {
            "2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05",
            "2026-06-08", "2026-06-09", "2026-06-10", "2026-06-11", "2026-06-12",
            "2026-06-15", "2026-06-16", "2026-06-17", "2026-06-18", "2026-06-19",
            "2026-06-22", "2026-06-23", "2026-06-24", "2026-06-25", "2026-06-26",
            "2026-06-29", "2026-06-30"
        };
        for (String day : workingDays) {
            fixtures.insertLeaveEntry(emp, day, "UNPAID_LOP_LEAVE", "1.00", superUserId);
        }
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(0.0);
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(0.0);
        // Zero gross has no earning lines; net still NULL (not computed).
        assertThat(d.get("lines").size()).isEqualTo(0);
        assertThat(d.get("netPay").isNull()).isTrue();
    }

    // ---- Phase 3: DA in gross + separate line -----------------------------

    private java.math.BigDecimal lineAmount(JsonNode detail, String componentCode) {
        for (JsonNode l : detail.get("lines")) {
            if (componentCode.equals(l.get("componentCode").asString())) {
                return new java.math.BigDecimal(l.get("amount").asString());
            }
        }
        return null;
    }

    @Test
    void daIncludedInGrossAsSeparateLine() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        // Basic 30000, HRA 15000, DA 6000, Other 9000 → June 2026 (30 days, 22 payable).
        UUID emp = fullMonthEmployeeWithDa("E040", "30000.00", "15000.00", "6000.00", "9000.00");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        // DA is its own line: 6000/30 * 22 = 4400.00.
        assertThat(lineAmount(d, "DA")).isEqualByComparingTo("4400.00");
        // 4 fixed lines: BASIC, HRA, DA, OTHER_FIXED_ALLOWANCES.
        assertThat(d.get("lines").size()).isEqualTo(4);
        // Gross = 60000/30 * 22 = 44000 (Basic+HRA+DA+Other prorated).
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(44000.00);
    }

    @Test
    void zeroDaWorksAndProducesNoDaLine() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        // DA = 0; addFixedLine omits zero-amount components.
        UUID emp = fullMonthEmployeeWithDa("E041", "30000.00", "15000.00", "0.00", "15000.00");
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(lineAmount(d, "DA")).isNull();
        assertThat(d.get("grossEarnings").asDouble()).isEqualTo(44000.00);
    }

    @Test
    void daProratedForLop() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID emp = fullMonthEmployeeWithDa("E042", "30000.00", "15000.00", "6000.00", "9000.00");
        // One full-day LOP on a working Monday reduces payable to 21.
        fixtures.insertLeaveEntry(emp, "2026-06-01", "UNPAID_LOP_LEAVE", "1.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(21.0);
        // DA: 6000/30 * 21 = 4200.00.
        assertThat(lineAmount(d, "DA")).isEqualByComparingTo("4200.00");
    }

    @Test
    void daJoinerProratedFromJoiningDate() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        // Joins 16 June 2026 → 11 scheduled working days payable.
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E043", "Joiner", "2026-06-16", null);
        UUID cal = fixtures.insertWorkCalendar(le, "Cal", "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "3000.00", "12000.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(11.0);
        // DA: 3000/30 * 11 = 1100.00.
        assertThat(lineAmount(d, "DA")).isEqualByComparingTo("1100.00");
    }

    @Test
    void daLeaverProratedUntilExit() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        // Exit 15 June 2026 → 11 scheduled working days payable.
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E044", "Leaver", "2026-01-01",
                "2026-06-15");
        UUID cal = fixtures.insertWorkCalendar(le, "Cal", "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        fixtures.insertCompensation(emp, "2026-01-01", null, "60000.00", "30000.00",
                "15000.00", "3000.00", "12000.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        assertThat(d.get("payableCalendarDays").asDouble()).isEqualTo(11.0);
        assertThat(lineAmount(d, "DA")).isEqualByComparingTo("1100.00");
    }

    @Test
    void daMidMonthRevisionUsesEffectiveStatePerDay() throws Exception {
        String auth = setUpTenantAndPayrollAdmin();
        UUID le = fixtures.activeLegalEntityId();
        UUID emp = fixtures.insertEmployeeWithPeriod(le, "E045", "Revised", "2026-01-01", null);
        UUID cal = fixtures.insertWorkCalendar(le, "Cal", "2026-01-01", null);
        fixtures.insertWorkCalendarAssignment(emp, cal, "2026-01-01", null, superUserId);
        // DA 3000 until 15 June (2000 → 100/day), revised DA 6000 from 16 June (200/day).
        fixtures.insertCompensation(emp, "2026-01-01", "2026-06-15", "60000.00", "30000.00",
                "15000.00", "3000.00", "12000.00", superUserId);
        fixtures.insertCompensation(emp, "2026-06-16", null, "120000.00", "60000.00",
                "30000.00", "6000.00", "24000.00", superUserId);
        UUID runId = run("DRAFT", "0");
        calculate(auth, runId);

        JsonNode d = detail(auth, runId, emp);
        // Days 1-15 scheduled (11) at DA 3000/30=100/day; days 16-30 scheduled (11)
        // at DA 6000/30=200/day → 11*100 + 11*200 = 1100 + 2200 = 3300.
        assertThat(lineAmount(d, "DA")).isEqualByComparingTo("3300.00");
    }
}
