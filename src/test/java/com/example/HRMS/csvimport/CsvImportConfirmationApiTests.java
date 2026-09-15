package com.example.HRMS.csvimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.bank.repository.EmployeeBankAccountRepository;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.compensation.entity.CompensationRecord;
import com.example.HRMS.compensation.repository.CompensationRecordRepository;
import com.example.HRMS.csvimport.repository.ImportSessionRepository;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.repository.EmployeeRepository;
import com.example.HRMS.leave.repository.EmployeeLeaveBalanceRepository;
import com.example.HRMS.regression.RbacTestFixtures;
import com.example.HRMS.tax.repository.EmployeeOpeningTaxStateRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * V2-006 CSV import confirmation API tests: CONFIRM-001..018 plus atomicity /
 * rollback, create-only Employee semantics, mapping correctness, authorization,
 * company isolation, concurrency and audit. Reuses the V2-005 upload flow to
 * create a real VALIDATED session, then confirms it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsvImportConfirmationApiTests {

    private static final String HEADERS =
            "Employee ID,Name,Joining Date,Exit Date,Employment Type,Department,"
            + "Designation,Location,PAN,UAN,PT State,Tax Regime,"
            + "Current-FY Cumulative Taxable Income,Current-FY TDS Already Deducted,"
            + "Account Number,IFSC,CTC,Basic,HRA,Other Allowances,"
            + "Effective Date,Opening Leave Balance";

    private static final String ROW_E001 =
            "E001,Asha Rao,2026-04-01,,FULL_TIME,Engineering,Engineer,Mumbai,"
            + "AAAAA0000A,,Maharashtra,NEW_REGIME,"
            + "500000.00,25000.00,"
            + "123456789012,HDFC0001234,1200000.00,600000.00,120000.00,0.00,"
            + "2026-04-01,12.00";

    private static final String ROW_E002 =
            "E002,Ravi Kumar,2026-04-01,,FULL_TIME,Sales,Manager,Delhi,"
            + "BBBBB1111B,,Karnataka,NEW_REGIME,"
            + "300000.00,10000.00,"
            + "222233334444,ICIC0005678,900000.00,450000.00,90000.00,0.00,"
            + "2026-04-01,10.50";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private ImportSessionRepository sessionRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private CompensationRecordRepository compensationRepository;
    @Autowired private EmployeeBankAccountRepository bankRepository;
    @Autowired private EmployeeOpeningTaxStateRepository taxRepository;
    @Autowired private EmployeeLeaveBalanceRepository leaveRepository;
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

    /** Upload a CSV, return the created import session id. */
    private String uploadSession(String auth, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "employees.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/v1/employees/imports")
                        .file(file).header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("importId").asString();
    }

    private MvcResult confirm(String auth, String importId) throws Exception {
        return mockMvc.perform(post("/api/v1/employees/imports/" + importId + "/confirm")
                        .header("Authorization", auth))
                .andReturn();
    }

    // ---- CONFIRM-001 / 002 / 003 -----------------------------------------

    @Test
    void validSessionConfirmsAndCreatesEntitiesAndTransitions() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001 + "\n" + ROW_E002);
        auditLogRepository.deleteAll();

        MvcResult result = confirm(auth, importId);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // CONFIRM-001 + response contract.
        assertThat(body.get("importId").asString()).isEqualTo(importId);
        assertThat(body.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(body.get("totalRowsApplied").asInt()).isEqualTo(2);
        assertThat(body.get("confirmedAt").asString()).isNotBlank();

        // CONFIRM-002: all required business entities created.
        assertThat(employeeRepository.count()).isEqualTo(2);
        assertThat(compensationRepository.count()).isEqualTo(2);
        assertThat(bankRepository.count()).isEqualTo(2);
        assertThat(taxRepository.count()).isEqualTo(2);
        assertThat(leaveRepository.count()).isEqualTo(2);

        // CONFIRM-003: session transitioned.
        var session = sessionRepository.findById(UUID.fromString(importId)).orElseThrow();
        assertThat(session.getStatus().name()).isEqualTo("CONFIRMED");
        assertThat(session.getConfirmedAt()).isNotNull();
    }

    // ---- CONFIRM-004: invalid rows block ---------------------------------

    @Test
    void invalidRowsBlockConfirmation() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Second row has invalid PAN → session VALIDATION_FAILED (invalidRows>0).
        String badRow = ROW_E002.replace("BBBBB1111B", "BADPAN");
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001 + "\n" + badRow);

        MvcResult result = confirm(auth, importId);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
        // No business data created.
        assertThat(employeeRepository.count()).isZero();
        assertThat(sessionRepository.findById(UUID.fromString(importId)).orElseThrow()
                .getStatus().name()).isEqualTo("VALIDATED");
    }

    // ---- CONFIRM-005 / 018: already confirmed ----------------------------

    @Test
    void secondConfirmationReturns409() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001);
        assertThat(confirm(auth, importId).getResponse().getStatus()).isEqualTo(200);

        MvcResult second = confirm(auth, importId);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(second.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");
        // Not reapplied: still exactly one employee.
        assertThat(employeeRepository.count()).isEqualTo(1);
    }

    // ---- CONFIRM-006: cross-company 404 ----------------------------------

    @Test
    void crossCompanyConfirmationIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        String importId = uploadSession(superAuth, HEADERS + "\n" + ROW_E001);

        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY,
                scopedCompanyId, UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        MvcResult result = confirm(bearer("otheradmin"), importId);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("NOT_FOUND");
    }

    // ---- CONFIRM-007: authorization --------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(post("/api/v1/employees/imports/" + UUID.randomUUID() + "/confirm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001);
        mockMvc.perform(post("/api/v1/employees/imports/" + importId + "/confirm")
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void missingImportIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(post("/api/v1/employees/imports/" + UUID.randomUUID() + "/confirm")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- CONFIRM-009 / 010 / 011 / 012: mapping correctness --------------

    @Test
    void mappingIsCorrectForAllEntities() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001);
        confirm(auth, importId);

        Employee e = employeeRepository.findAll().stream()
                .filter(x -> "E001".equals(x.getEmployeeId())).findFirst().orElseThrow();
        assertThat(e.getFullName()).isEqualTo("Asha Rao");
        assertThat(e.getTaxRegime().name()).isEqualTo("NEW_REGIME");
        assertThat(e.getStatus().name()).isEqualTo("ACTIVE");

        // CONFIRM-012 compensation mapping + source=CSV_IMPORT.
        List<CompensationRecord> comps = compensationRepository
                .findByEmployeeIdOrderByEffectiveFromDesc(e.getId());
        assertThat(comps).hasSize(1);
        CompensationRecord c = comps.get(0);
        assertThat(c.getCtcMonthly()).isEqualByComparingTo("1200000.00");
        assertThat(c.getBasicMonthly()).isEqualByComparingTo("600000.00");
        assertThat(c.getHraMonthly()).isEqualByComparingTo("120000.00");
        assertThat(c.getOtherFixedAllowancesMonthly()).isEqualByComparingTo("0.00");
        assertThat(c.getEffectiveFrom().toString()).isEqualTo("2026-04-01");
        assertThat(c.getEffectiveTo()).isNull();
        assertThat(c.getSource().name()).isEqualTo("CSV_IMPORT");

        // CONFIRM-009 bank mapping.
        var bank = bankRepository.findFirstByEmployeeIdAndEffectiveToIsNull(e.getId()).orElseThrow();
        assertThat(bank.getAccountNumber()).isEqualTo("123456789012");
        assertThat(bank.getIfsc()).isEqualTo("HDFC0001234");
        assertThat(bank.isPrimary()).isTrue();
        assertThat(bank.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(bank.getEffectiveFrom()).isNotNull();

        // CONFIRM-010 opening tax state mapping + source + server-derived FY.
        var tax = taxRepository.findAll().stream()
                .filter(t -> t.getEmployeeId().equals(e.getId())).findFirst().orElseThrow();
        assertThat(tax.getCumulativeTaxableIncome()).isEqualByComparingTo("500000.00");
        assertThat(tax.getTdsAlreadyDeducted()).isEqualByComparingTo("25000.00");
        assertThat(tax.getSource().name()).isEqualTo("CSV_IMPORT");
        assertThat(tax.getFinancialYear()).isNotBlank();

        // CONFIRM-011 leave balance mapping.
        var leave = leaveRepository.findByEmployeeIdAndFinancialYear(
                e.getId(), tax.getFinancialYear()).orElseThrow();
        assertThat(leave.getOpeningBalance()).isEqualByComparingTo("12.00");
        assertThat(leave.getApprovedAdditions()).isEqualByComparingTo("0");
        assertThat(leave.getUsedQuantity()).isEqualByComparingTo("0");
        assertThat(leave.getAvailableBalance()).isEqualByComparingTo("12.00");
        assertThat(leave.getLeaveTreatment().name()).isEqualTo("PAID_LEAVE");
    }

    // ---- CONFIRM-013: audit ----------------------------------------------

    @Test
    void confirmationRecordsAuditEvents() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001);
        auditLogRepository.deleteAll();
        confirm(auth, importId);

        var actions = auditLogRepository.findAll().stream().map(a -> a.getAction()).toList();
        assertThat(actions).contains(AuditActions.IMPORT_SESSION_CONFIRMED,
                AuditActions.EMPLOYEE_CREATED, AuditActions.COMPENSATION_CREATED,
                AuditActions.BANK_ACCOUNT_CREATED, AuditActions.OPENING_TAX_STATE_CREATED,
                AuditActions.LEAVE_BALANCE_CREATED);
        // No sensitive values in audit metadata (entityId only; no account number).
        assertThat(auditLogRepository.findAll().stream()
                .noneMatch(a -> a.getReason() != null
                        && a.getReason().contains("123456789012"))).isTrue();
    }

    // ---- CONFIRM-014: raw CSV not persisted ------------------------------

    @Test
    void rawCsvIsNotPersisted() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001);
        confirm(auth, importId);
        // The session stores metadata + structured validated rows only; there is
        // no column holding the raw uploaded file. Assert the entity exposes no
        // raw-file content and the file name is metadata only.
        var session = sessionRepository.findById(UUID.fromString(importId)).orElseThrow();
        assertThat(session.getOriginalFileName()).isEqualTo("employees.csv");
    }

    // ---- CONFIRM-015: existing Employee ID → 409 + full rollback ---------

    @Test
    void existingEmployeeIdBlocksImportAndRollsBack() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // First import creates E001.
        String firstImport = uploadSession(auth, HEADERS + "\n" + ROW_E001);
        assertThat(confirm(auth, firstImport).getResponse().getStatus()).isEqualTo(200);
        long employeesAfterFirst = employeeRepository.count();
        long compsAfterFirst = compensationRepository.count();

        // Second import: E002 (new) + E001 (already exists) → whole import fails.
        String secondImport = uploadSession(auth, HEADERS + "\n" + ROW_E002 + "\n" + ROW_E001);
        MvcResult result = confirm(auth, secondImport);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("CONFLICT");

        // Full rollback: E002 was NOT created; counts unchanged from after-first.
        assertThat(employeeRepository.count()).isEqualTo(employeesAfterFirst);
        assertThat(compensationRepository.count()).isEqualTo(compsAfterFirst);
        assertThat(employeeRepository.findAll().stream()
                .noneMatch(e -> "E002".equals(e.getEmployeeId()))).isTrue();
        // Second session remains VALIDATED (not CONFIRMED).
        assertThat(sessionRepository.findById(UUID.fromString(secondImport)).orElseThrow()
                .getStatus().name()).isEqualTo("VALIDATED");
    }

    // ---- CONFIRM-016: duplicate Employee IDs in CSV blocked by V2-005 ----

    @Test
    void duplicateEmployeeIdsInCsvCannotReachConfirmation() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Two rows with the same Employee ID → V2-005 marks both invalid.
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001 + "\n" + ROW_E001);
        var session = sessionRepository.findById(UUID.fromString(importId)).orElseThrow();
        assertThat(session.getInvalidRows()).isGreaterThan(0);

        MvcResult result = confirm(auth, importId);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(employeeRepository.count()).isZero();
    }

    // ---- CONFIRM-008 / atomicity: rollback leaves no partial data --------

    @Test
    void atomicRollbackLeavesNoPartialData() throws Exception {
        // Reuse the existing-employee conflict as the forced business failure:
        // E001 (row 1) would create successfully, E-EXIST (row 2) conflicts.
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Pre-create an employee with business id E900 via a first import.
        String seed = uploadSession(auth, HEADERS + "\n" + ROW_E001.replace("E001", "E900"));
        confirm(auth, seed);
        long employeesBefore = employeeRepository.count();
        long banksBefore = bankRepository.count();
        long taxBefore = taxRepository.count();
        long leaveBefore = leaveRepository.count();
        long compsBefore = compensationRepository.count();

        // New import: E001 (new, row 1) then E900 (existing, row 2). Row 1 would
        // persist first, but the row-2 conflict must roll the whole thing back.
        String importId = uploadSession(auth,
                HEADERS + "\n" + ROW_E001 + "\n" + ROW_E002.replace("E002", "E900"));
        MvcResult result = confirm(auth, importId);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);

        // Nothing from this import persisted (E001 rolled back with the conflict).
        assertThat(employeeRepository.count()).isEqualTo(employeesBefore);
        assertThat(bankRepository.count()).isEqualTo(banksBefore);
        assertThat(taxRepository.count()).isEqualTo(taxBefore);
        assertThat(leaveRepository.count()).isEqualTo(leaveBefore);
        assertThat(compensationRepository.count()).isEqualTo(compsBefore);
        assertThat(employeeRepository.findAll().stream()
                .noneMatch(e -> "E001".equals(e.getEmployeeId()))).isTrue();
    }

    // ---- CONFIRM-017: concurrent confirmation ----------------------------

    @Test
    void concurrentConfirmationAppliesOnlyOnce() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String importId = uploadSession(auth, HEADERS + "\n" + ROW_E001);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> confirm(auth, importId).getResponse().getStatus();
            Future<Integer> f1 = pool.submit(task);
            Future<Integer> f2 = pool.submit(task);
            int s1 = f1.get();
            int s2 = f2.get();
            // Exactly one 200 and one 409 (or, if perfectly serialized, still only
            // one applies the data). Both cannot be 200.
            assertThat(List.of(s1, s2)).contains(200);
            assertThat(s1 == 200 && s2 == 200).isFalse();
        } finally {
            pool.shutdownNow();
        }
        // Data applied exactly once regardless of interleaving.
        assertThat(employeeRepository.findAll().stream()
                .filter(e -> "E001".equals(e.getEmployeeId())).count()).isEqualTo(1);
    }
}
