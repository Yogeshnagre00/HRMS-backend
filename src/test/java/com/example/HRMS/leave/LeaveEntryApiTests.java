package com.example.HRMS.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.leave.repository.EmployeeLeaveBalanceRepository;
import com.example.HRMS.leave.repository.LeaveEntryRepository;
import com.example.HRMS.regression.RbacTestFixtures;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * V2-008A.5 Leave Entry API tests: LV-001..013 (applicable) plus balance
 * mutation, insufficient/duplicate/attendance-conflict, cancellation, scope,
 * authorization, ordering and audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveEntryApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private LeaveEntryRepository leaveEntryRepository;
    @Autowired private EmployeeLeaveBalanceRepository balanceRepository;
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
        // FY starts 2025-04-01 → current-FY leave balance is used by paid leave.
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"legalName\":\"Acme India Pvt Ltd\",\"countryCode\":\"IN\","
                                + "\"pan\":\"AAAAA0000A\",\"financialYearStart\":\"2025-04-01\"}"))
                .andExpect(status().isCreated());
    }

    /** Employee joining 2025-04-01, exit 2027-12-31 (covers current FY). */
    private String createEmployee(String auth, String empBizId) throws Exception {
        String created = mockMvc.perform(post("/api/v1/employees").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"employeeId\":\"" + empBizId + "\",\"fullName\":\"Asha Rao\","
                                + "\"joiningDate\":\"2025-04-01\",\"exitDate\":\"2027-12-31\","
                                + "\"employmentType\":\"FULL_TIME\",\"pan\":\"AAAAA0000A\","
                                + "\"taxRegime\":\"NEW_REGIME\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asString();
    }

    /** Set the employee's current-FY paid-leave balance via the V2-004 endpoint. */
    private void setLeaveBalance(String auth, String empId, String opening) throws Exception {
        mockMvc.perform(put("/api/v1/employees/" + empId + "/leave-balance")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"openingBalance\":" + opening + ",\"approvedAdditions\":0.00,"
                                + "\"usedQuantity\":0.00}"))
                .andExpect(status().isOk());
    }

    private MvcResult postLeave(String auth, String empId, String date, String treatment,
                                String qty) throws Exception {
        String body = "{\"employeeId\":\"" + empId + "\",\"leaveDate\":\"" + date + "\","
                + "\"treatment\":\"" + treatment + "\",\"quantity\":" + qty + "}";
        return mockMvc.perform(post("/api/v1/leave/entries").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andReturn();
    }

    /**
     * The current-FY balance row (FY is server-derived from the current business
     * date and the legal entity's FY start = April 1). Leave consumption always
     * targets the current FY, regardless of the leave date's own FY.
     */
    private com.example.HRMS.leave.entity.EmployeeLeaveBalance currentBalance(String empId) {
        return balanceRepository.findAll().stream()
                .filter(b -> b.getEmployeeId().equals(UUID.fromString(empId)))
                .findFirst().orElseThrow();
    }

    private double usedQty(String empId) {
        return currentBalance(empId).getUsedQuantity().doubleValue();
    }

    private double availableQty(String empId) {
        return currentBalance(empId).getAvailableBalance().doubleValue();
    }

    // ---- LV-001 / LV-008: paid leave + balance consumption ----------------

    @Test
    void createPaidLeaveConsumesBalance() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        auditLogRepository.deleteAll();

        MvcResult r = postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("treatment").asString()).isEqualTo("PAID_LEAVE");
        assertThat(body.get("status").asString()).isEqualTo("RECORDED");
        assertThat(usedQty(empId)).isEqualTo(1.0);
        assertThat(availableQty(empId)).isEqualTo(4.0);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.LEAVE_ENTRY_CREATED.equals(a.getAction()))).isTrue();
    }

    // ---- LV-002 / LV-010: unpaid leave does not touch balance -------------

    @Test
    void createUnpaidLeaveDoesNotTouchBalance() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        assertThat(postLeave(auth, empId, "2026-06-10", "UNPAID_LOP_LEAVE", "1.0")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(usedQty(empId)).isEqualTo(0.0);
        assertThat(availableQty(empId)).isEqualTo(5.0);
    }

    // ---- LV-003: list -----------------------------------------------------

    @Test
    void listLeaveEntriesPaginatedAndOrdered() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        postLeave(auth, empId, "2026-06-20", "PAID_LEAVE", "1.0");
        postLeave(auth, empId, "2026-06-10", "UNPAID_LOP_LEAVE", "0.5");
        MvcResult r = mockMvc.perform(get("/api/v1/leave/entries?employeeId=" + empId
                        + "&page=0&size=10").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("content").get(0).get("leaveDate").asString()).isEqualTo("2026-06-10");
        assertThat(body.get("content").get(1).get("leaveDate").asString()).isEqualTo("2026-06-20");
    }

    // ---- LV-004 / LV-007: insufficient balance → 409, no mutation ---------

    @Test
    void insufficientPaidLeaveReturns409AndNoMutation() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "0.50");
        MvcResult r = postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
        // No entry, no mutation.
        assertThat(leaveEntryRepository.findByEmployeeId(UUID.fromString(empId),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                .isZero();
        assertThat(usedQty(empId)).isEqualTo(0.0);
        assertThat(availableQty(empId)).isEqualTo(0.5);
    }

    @Test
    void paidLeaveWithoutBalanceRowReturns409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        // No leave-balance set → treated as insufficient.
        assertThat(postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "0.5")
                .getResponse().getStatus()).isEqualTo(409);
    }

    // ---- LV-009: cancellation reverses once -------------------------------

    @Test
    void cancelPaidLeaveReversesOnce() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        String id = objectMapper.readTree(postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0")
                .getResponse().getContentAsString()).get("id").asString();
        assertThat(usedQty(empId)).isEqualTo(1.0);

        // Cancel via DELETE.
        mockMvc.perform(delete("/api/v1/leave/entries/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(usedQty(empId)).isEqualTo(0.0);
        assertThat(availableQty(empId)).isEqualTo(5.0);

        // Double cancellation → 409, no second reversal.
        mockMvc.perform(delete("/api/v1/leave/entries/" + id).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(usedQty(empId)).isEqualTo(0.0);
    }

    @Test
    void cancelViaPutTransitionsToCancelled() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        String id = objectMapper.readTree(postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0")
                .getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(put("/api/v1/leave/entries/" + id).header("Authorization", auth)
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(usedQty(empId)).isEqualTo(0.0);
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.LEAVE_ENTRY_CANCELLED.equals(a.getAction()))).isTrue();
    }

    @Test
    void cancelUnpaidLeaveDoesNotTouchBalance() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        String id = objectMapper.readTree(postLeave(auth, empId, "2026-06-10", "UNPAID_LOP_LEAVE",
                "1.0").getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(delete("/api/v1/leave/entries/" + id).header("Authorization", auth))
                .andExpect(status().isOk());
        assertThat(usedQty(empId)).isEqualTo(0.0);
        assertThat(availableQty(empId)).isEqualTo(5.0);
    }

    // ---- cancelled entry does not block a new entry for the same date -----

    @Test
    void cancelledEntryAllowsReplacement() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        String id = objectMapper.readTree(postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0")
                .getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(delete("/api/v1/leave/entries/" + id).header("Authorization", auth))
                .andExpect(status().isOk());
        // Same date again after cancellation → allowed.
        assertThat(postLeave(auth, empId, "2026-06-10", "UNPAID_LOP_LEAVE", "1.0")
                .getResponse().getStatus()).isEqualTo(201);
    }

    // ---- duplicate + attendance conflict ----------------------------------

    @Test
    void duplicateRecordedEntryReturns409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        assertThat(postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(postLeave(auth, empId, "2026-06-10", "UNPAID_LOP_LEAVE", "1.0")
                .getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void leaveAttendanceSameDateConflictReturns409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        // Record an attendance exception first.
        mockMvc.perform(post("/api/v1/attendance/exceptions").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"employeeId\":\"" + empId + "\",\"attendanceDate\":\"2026-06-10\","
                                + "\"exceptionType\":\"FULL_DAY_ABSENCE\",\"quantity\":1.0}"))
                .andExpect(status().isCreated());
        MvcResult r = postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0");
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
    }

    // ---- LV-005: employment period + quantity/treatment validation --------

    @Test
    void beforeJoiningRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        assertThat(postLeave(auth, empId, "2025-03-31", "PAID_LEAVE", "1.0")
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void afterExitRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        assertThat(postLeave(auth, empId, "2028-01-01", "UNPAID_LOP_LEAVE", "1.0")
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void invalidQuantityRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "5.00");
        assertThat(postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "0.75")
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void invalidTreatmentRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        String body = "{\"employeeId\":\"" + empId + "\",\"leaveDate\":\"2026-06-10\","
                + "\"treatment\":\"SICK\",\"quantity\":1.0}";
        MvcResult r = mockMvc.perform(post("/api/v1/leave/entries").header("Authorization", auth)
                        .contentType("application/json").content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void missingEmployeeIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        assertThat(postLeave(auth, UUID.randomUUID().toString(), "2026-06-10", "UNPAID_LOP_LEAVE",
                "1.0").getResponse().getStatus()).isEqualTo(404);
    }

    // ---- LV-012: concurrent paid leave serialized, never negative ---------

    @Test
    void concurrentPaidLeaveNeverOverConsumes() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "1.00"); // only 1.0 available

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> t1 = () -> postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0")
                    .getResponse().getStatus();
            Callable<Integer> t2 = () -> postLeave(auth, empId, "2026-06-11", "PAID_LEAVE", "1.0")
                    .getResponse().getStatus();
            Future<Integer> f1 = pool.submit(t1);
            Future<Integer> f2 = pool.submit(t2);
            int s1 = f1.get();
            int s2 = f2.get();
            // Exactly one succeeds (201); the other is insufficient (409).
            assertThat(java.util.List.of(s1, s2)).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        // Balance never negative; used exactly 1.0.
        assertThat(usedQty(empId)).isEqualTo(1.0);
        assertThat(availableQty(empId)).isEqualTo(0.0);
    }

    // ---- LV-013: payroll ownership invariant (no payroll mutation) --------

    @Test
    void leaveConsumptionOwnedByLeaveLayerNotPayroll() throws Exception {
        // Leave balance is mutated by the leave layer at entry time (not payroll).
        // There is no payroll calculation in this slice; assert the balance
        // reflects leave-entry consumption directly and deterministically.
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String empId = createEmployee(auth, "E001");
        setLeaveBalance(auth, empId, "3.00");
        postLeave(auth, empId, "2026-06-10", "PAID_LEAVE", "1.0");
        postLeave(auth, empId, "2026-06-11", "PAID_LEAVE", "0.5");
        assertThat(usedQty(empId)).isEqualTo(1.5);
        assertThat(availableQty(empId)).isEqualTo(1.5);
    }

    // ---- authorization + scope --------------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/leave/entries?employeeId=" + UUID.randomUUID()))
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
        mockMvc.perform(get("/api/v1/leave/entries?employeeId=" + empId)
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void crossCompanyLeaveEntryAccessIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String empId = createEmployee(superAuth, "E001");
        setLeaveBalance(superAuth, empId, "5.00");
        String id = objectMapper.readTree(postLeave(superAuth, empId, "2026-06-10",
                "UNPAID_LOP_LEAVE", "1.0").getResponse().getContentAsString()).get("id").asString();
        UUID otherCompany = fixtures.insertCompany("Other Co", "INACTIVE");
        UUID otherCa = fixtures.createUser("otherca", ScopeType.COMPANY, otherCompany,
                UserStatus.ACTIVE);
        fixtures.assignRole(otherCa, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/leave/entries/" + id)
                        .header("Authorization", bearer("otherca")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingLeaveEntryIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(get("/api/v1/leave/entries/" + UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }
}

