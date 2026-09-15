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
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.csvimport.repository.ImportSessionRepository;
import com.example.HRMS.csvimport.repository.ImportSessionRowRepository;
import com.example.HRMS.employee.repository.EmployeeRepository;
import com.example.HRMS.regression.RbacTestFixtures;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * V2-005 CSV validation API tests covering canonical headers, row validation,
 * result structure, import-session persistence, GET session, company isolation,
 * authorization, no-mutation invariant, and stable error codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsvImportApiTests {

    // Canonical header line (API §11.2, exact order, exact case).
    private static final String HEADERS =
            "Employee ID,Name,Joining Date,Exit Date,Employment Type,Department,"
            + "Designation,Location,PAN,UAN,PT State,Tax Regime,"
            + "Current-FY Cumulative Taxable Income,Current-FY TDS Already Deducted,"
            + "Account Number,IFSC,CTC,Basic,HRA,Other Allowances,"
            + "Effective Date,Opening Leave Balance";

    // A fully valid data row.
    private static final String VALID_ROW =
            "E001,Asha Rao,2026-04-01,,FULL_TIME,Engineering,Engineer,Mumbai,"
            + "AAAAA0000A,,Maharashtra,NEW_REGIME,"
            + "500000.00,25000.00,"
            + "123456789012,HDFC0001234,1200000.00,600000.00,120000.00,0.00,"
            + "2026-04-01,12.00";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private ImportSessionRepository sessionRepository;
    @Autowired private ImportSessionRowRepository rowRepository;
    @Autowired private EmployeeRepository employeeRepository;
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

    private MvcResult upload(String auth, String csvContent) throws Exception {
        return upload(auth, csvContent, "employees.csv");
    }

    private MvcResult upload(String auth, String csvContent, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename,
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/employees/imports")
                        .file(file).header("Authorization", auth))
                .andReturn();
    }

    // ---- FILE LEVEL -------------------------------------------------------

    @Test
    void validCsvPassesAndPersistsSession() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        auditLogRepository.deleteAll();
        String csv = HEADERS + "\n" + VALID_ROW;

        MvcResult result = upload(auth, csv);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_PASSED");
        assertThat(body.get("totalRows").asInt()).isEqualTo(1);
        assertThat(body.get("validRows").asInt()).isEqualTo(1);
        assertThat(body.get("invalidRows").asInt()).isEqualTo(0);
        String importId = body.get("importId").asString();
        assertThat(importId).isNotBlank();
        // Session must be persisted.
        assertThat(sessionRepository.findById(UUID.fromString(importId))).isPresent();
        assertThat(rowRepository.findByImportSessionIdOrderByRowNumberAsc(
                UUID.fromString(importId))).hasSize(1);
        // Audit event.
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.IMPORT_SESSION_VALIDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void emptyFileReturnsBlockingIssue() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult result = upload(auth, "");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("issues").size()).isGreaterThan(0);
        assertThat(body.get("issues").get(0).get("code").asString()).isEqualTo("FILE_EMPTY");
        assertThat(body.get("issues").get(0).get("severity").asString()).isEqualTo("BLOCKING");
    }

    @Test
    void missingRequiredHeaderIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Omit "PAN" from header line.
        String badHeaders = "Employee ID,Name,Joining Date,Exit Date,Employment Type,Department,"
                + "Designation,Location,UAN,PT State,Tax Regime,"
                + "Current-FY Cumulative Taxable Income,Current-FY TDS Already Deducted,"
                + "Account Number,IFSC,CTC,Basic,HRA,Other Allowances,"
                + "Effective Date,Opening Leave Balance";
        MvcResult result = upload(auth, badHeaders + "\n" + VALID_ROW);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean hasMissingPan = false;
        for (JsonNode issue : body.get("issues")) {
            if ("HEADER_MISSING".equals(issue.get("code").asString())
                    && "PAN".equals(issue.get("field").asString())) {
                hasMissingPan = true;
            }
        }
        assertThat(hasMissingPan).isTrue();
    }

    @Test
    void duplicateHeaderIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Repeat "Employee ID".
        String badHeaders = HEADERS + ",Employee ID";
        MvcResult result = upload(auth, badHeaders + "\n" + VALID_ROW + ",X");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean hasDup = false;
        for (JsonNode issue : body.get("issues")) {
            if ("HEADER_DUPLICATE".equals(issue.get("code").asString())) {
                hasDup = true;
            }
        }
        assertThat(hasDup).isTrue();
    }

    @Test
    void unknownHeaderIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String badHeaders = HEADERS.replace("PAN", "PAN_NUMBER");
        MvcResult result = upload(auth, badHeaders + "\n" + VALID_ROW);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean hasUnknown = false;
        for (JsonNode issue : body.get("issues")) {
            if ("HEADER_UNKNOWN".equals(issue.get("code").asString())) {
                hasUnknown = true;
            }
        }
        assertThat(hasUnknown).isTrue();
    }

    @Test
    void caseMismatchInHeaderIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // "employee ID" instead of "Employee ID".
        String badHeaders = HEADERS.replace("Employee ID", "employee ID");
        MvcResult result = upload(auth, badHeaders + "\n" + VALID_ROW);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
    }

    // ---- ROW LEVEL -------------------------------------------------------

    @Test
    void missingRequiredFieldInRowIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Name is blank.
        String row = "E001,,2026-04-01,,FULL_TIME,,,,"
                + "AAAAA0000A,,Maharashtra,NEW_REGIME,"
                + "500000.00,25000.00,"
                + "123456789012,HDFC0001234,1200000.00,600000.00,120000.00,0.00,"
                + "2026-04-01,12.00";
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("invalidRows").asInt()).isEqualTo(1);
    }

    @Test
    void duplicateEmployeeIdWithinCsvFlagsBothRows() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row2 = VALID_ROW; // same E001
        String row3 = VALID_ROW.replace("E001", "E001"); // same E001 again
        MvcResult result = upload(auth, HEADERS + "\n" + row2 + "\n" + row3);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        int dupCount = 0;
        for (JsonNode issue : body.get("issues")) {
            if ("DUPLICATE_EMPLOYEE_ID_IN_FILE".equals(issue.get("code").asString())) {
                dupCount++;
            }
        }
        // Both rows with the duplicate ID should be flagged.
        assertThat(dupCount).isEqualTo(2);
    }

    @Test
    void invalidJoiningDateIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace("2026-04-01,,FULL_TIME", "not-a-date,,FULL_TIME");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean hasInvalidDate = false;
        for (JsonNode issue : body.get("issues")) {
            if ("INVALID_DATE".equals(issue.get("code").asString())
                    && "Joining Date".equals(issue.get("field").asString())) {
                hasInvalidDate = true;
            }
        }
        assertThat(hasInvalidDate).isTrue();
    }

    @Test
    void exitBeforeJoiningIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // joiningDate=2026-04-10, exitDate=2026-04-01 → exit before joining.
        String row = VALID_ROW.replace("2026-04-01,,FULL_TIME", "2026-04-10,2026-04-01,FULL_TIME");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean found = false;
        for (JsonNode issue : body.get("issues")) {
            if ("EXIT_BEFORE_JOINING".equals(issue.get("code").asString())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void invalidPanIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace(",AAAAA0000A,,Maharashtra", ",BADPAN,,Maharashtra");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean found = false;
        for (JsonNode issue : body.get("issues")) {
            if ("INVALID_PAN".equals(issue.get("code").asString())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
        // PAN value must not appear in the message.
        for (JsonNode issue : body.get("issues")) {
            assertThat(issue.get("message").asString()).doesNotContain("BADPAN");
        }
    }

    @Test
    void blankUanIsAccepted() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // UAN column already blank in VALID_ROW (AAAAA0000A,,Maharashtra).
        MvcResult result = upload(auth, HEADERS + "\n" + VALID_ROW);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_PASSED");
    }

    @Test
    void newRegimeIsAccepted() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult result = upload(auth, HEADERS + "\n" + VALID_ROW);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_PASSED");
    }

    @Test
    void oldRegimeIsAcceptedWithWarningNotRejected() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace(",NEW_REGIME,", ",OLD_REGIME,");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // Old Regime is accepted — status should be PASSED (no BLOCKING issues).
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_PASSED");
        // But a WARNING is surfaced.
        boolean hasWarning = false;
        for (JsonNode issue : body.get("issues")) {
            if ("OLD_REGIME_TDS_UNSUPPORTED".equals(issue.get("code").asString())
                    && "WARNING".equals(issue.get("severity").asString())) {
                hasWarning = true;
            }
        }
        assertThat(hasWarning).isTrue();
    }

    @Test
    void unsupportedPtStateIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace(",Maharashtra,", ",Gujarat,");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean found = false;
        for (JsonNode issue : body.get("issues")) {
            if ("INVALID_PT_STATE".equals(issue.get("code").asString())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void negativeTaxableIncomeIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace("500000.00,25000.00,", "-1.00,25000.00,");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean found = false;
        for (JsonNode issue : body.get("issues")) {
            if ("NEGATIVE_AMOUNT".equals(issue.get("code").asString())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void invalidCtcIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace(",1200000.00,600000.00,", ",not-a-number,600000.00,");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean found = false;
        for (JsonNode issue : body.get("issues")) {
            if ("INVALID_AMOUNT".equals(issue.get("code").asString())
                    && "CTC".equals(issue.get("field").asString())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void negativeOpeningLeaveBalanceIsBlocking() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String row = VALID_ROW.replace(",12.00", ",-1.00");
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        boolean found = false;
        for (JsonNode issue : body.get("issues")) {
            if ("NEGATIVE_QUANTITY".equals(issue.get("code").asString())
                    && "Opening Leave Balance".equals(issue.get("field").asString())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void multipleIssuesOnSameRowAllReported() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Blank name AND invalid PAN AND invalid joining date.
        String row = "E001,,bad-date,,FULL_TIME,,,,"
                + "BADPAN,,Maharashtra,NEW_REGIME,"
                + "500000.00,25000.00,"
                + "123456789012,HDFC0001234,1200000.00,600000.00,120000.00,0.00,"
                + "2026-04-01,12.00";
        MvcResult result = upload(auth, HEADERS + "\n" + row);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        // At least 3 issues on the same row.
        assertThat(body.get("issues").size()).isGreaterThanOrEqualTo(3);
        for (JsonNode issue : body.get("issues")) {
            assertThat(issue.get("rowNumber").asInt()).isEqualTo(2);
        }
    }

    // ---- RESULT STRUCTURE ------------------------------------------------

    @Test
    void responseContainsImportIdAndCounts() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String csv = HEADERS + "\n" + VALID_ROW + "\n"
                + VALID_ROW.replace("E001", "E002");
        MvcResult result = upload(auth, csv);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("importId").asString()).isNotBlank();
        assertThat(body.get("totalRows").asInt()).isEqualTo(2);
        assertThat(body.get("validRows").asInt()).isEqualTo(2);
        assertThat(body.get("invalidRows").asInt()).isEqualTo(0);
        assertThat(body.has("warningRows")).isTrue();
        assertThat(body.has("issues")).isTrue();
    }

    @Test
    void issueRowNumberIsPhysicalIncludingHeader() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Valid row 1 (physical row 2), invalid row 2 (physical row 3).
        String csv = HEADERS + "\n" + VALID_ROW + "\n"
                + VALID_ROW.replace(",AAAAA0000A,", ",BADPAN,");
        MvcResult result = upload(auth, csv);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // There should be an issue on the second data row = physical row 3.
        boolean foundRow3Issue = false;
        for (JsonNode issue : body.get("issues")) {
            if (issue.get("rowNumber").asInt() == 3) {
                foundRow3Issue = true;
            }
            // No issue should reference row 1 (the header) or row 4+.
            assertThat(issue.get("rowNumber").asInt()).isBetween(2, 3);
        }
        assertThat(foundRow3Issue).isTrue();
    }

    @Test
    void issuesAreDeterministicallyOrdered() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        // Multiple issues; verify ordering is stable.
        String row = "E001,,bad-date,,FULL_TIME,,,,"
                + "BADPAN,,Maharashtra,NEW_REGIME,"
                + "500000.00,25000.00,"
                + "123456789012,HDFC0001234,1200000.00,600000.00,120000.00,0.00,"
                + "2026-04-01,12.00";
        MvcResult r1 = upload(auth, HEADERS + "\n" + row);
        MvcResult r2 = upload(auth, HEADERS + "\n" + row);
        JsonNode b1 = objectMapper.readTree(r1.getResponse().getContentAsString());
        JsonNode b2 = objectMapper.readTree(r2.getResponse().getContentAsString());
        // Codes in the same order both times.
        assertThat(b1.get("issues").toPrettyString())
                .isEqualTo(b2.get("issues").toPrettyString());
    }

    // ---- SESSION PERSISTENCE + GET ---------------------------------------

    @Test
    void getSessionReturnsSameResult() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult uploadResult = upload(auth, HEADERS + "\n" + VALID_ROW);
        String importId = objectMapper.readTree(
                uploadResult.getResponse().getContentAsString()).get("importId").asString();

        mockMvc.perform(get("/api/v1/employees/imports/" + importId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value(importId))
                .andExpect(jsonPath("$.status").value("VALIDATION_PASSED"));
    }

    @Test
    void getSessionStatusAfterUpload() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult uploadResult = upload(auth, HEADERS + "\n" + VALID_ROW);
        String importId = objectMapper.readTree(
                uploadResult.getResponse().getContentAsString()).get("importId").asString();

        // Session status must be VALIDATED (not CONFIRMED - that is V2-006).
        var session = sessionRepository.findById(UUID.fromString(importId));
        assertThat(session).isPresent();
        assertThat(session.get().getStatus().name()).isEqualTo("VALIDATED");
        assertThat(session.get().getConfirmedAt()).isNull();
    }

    @Test
    void getMissingSessionIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(get("/api/v1/employees/imports/" + UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- NO BUSINESS-DATA MUTATION ---------------------------------------

    @Test
    void validationDoesNotCreateEmployeeRecords() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        long empBefore = employeeRepository.count();
        // Upload a valid single-row CSV twice.
        upload(auth, HEADERS + "\n" + VALID_ROW);
        upload(auth, HEADERS + "\n" + VALID_ROW);
        assertThat(employeeRepository.count()).isEqualTo(empBefore);
    }

    // ---- COMPANY ISOLATION -----------------------------------------------

    @Test
    void crossCompanySessionAccessIs404() throws Exception {
        String superAuth = bearer("superadmin");
        createCompanyAndLegalEntity(superAuth);
        MvcResult uploadResult = upload(superAuth, HEADERS + "\n" + VALID_ROW);
        String importId = objectMapper.readTree(
                uploadResult.getResponse().getContentAsString()).get("importId").asString();

        // Other admin belongs to a DIFFERENT company (scopedCompanyId, INACTIVE).
        UUID otherAdmin = fixtures.createUser("otheradmin", ScopeType.COMPANY,
                scopedCompanyId, UserStatus.ACTIVE);
        fixtures.assignRole(otherAdmin, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        // cross-company → 404 (no disclosure).
        mockMvc.perform(get("/api/v1/employees/imports/" + importId)
                        .header("Authorization", bearer("otheradmin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- AUTHORIZATION ---------------------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "e.csv",
                "text/csv", (HEADERS + "\n" + VALID_ROW).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/employees/imports").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MockMultipartFile file = new MockMultipartFile("file", "e.csv",
                "text/csv", (HEADERS + "\n" + VALID_ROW).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/employees/imports").file(file)
                        .header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- ERROR CONTRACT --------------------------------------------------

    @Test
    void issueCodeIsStableMachineReadable() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        MvcResult result = upload(auth, HEADERS + "\n"
                + VALID_ROW.replace(",AAAAA0000A,,Maharashtra", ",BADPAN,,Maharashtra"));
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode issue : body.get("issues")) {
            // code must be non-empty and not contain spaces (stable machine key).
            assertThat(issue.get("code").asString()).isNotBlank();
            assertThat(issue.get("code").asString()).doesNotContain(" ");
            assertThat(issue.get("severity").asString())
                    .isIn("BLOCKING", "WARNING", "INFORMATIONAL");
        }
    }
}
