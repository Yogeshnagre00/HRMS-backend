package com.example.HRMS.statutory;

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
 * V0-005 Statutory Configuration API tests: explicit PF/PT/TDS applicability,
 * PF registration-state rules, supported/unsupported PT states, TDS policy,
 * rule-version referencing, effective-date validation, authorization (401/403),
 * 404, validation (400), and audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatutoryConfigurationApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID ruleVersionSetId;
    private UUID scopedCompanyId;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        // Platform admin holds company.admin (via SUPER_ADMIN) to manage config.
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
        // PAYROLL_ADMIN lacks company.admin; its INACTIVE company keeps the single
        // active-company invariant intact for the 403 test.
        scopedCompanyId = fixtures.insertCompany("Scoped Co", "INACTIVE");
        UUID pay = fixtures.createUser("payadmin", ScopeType.COMPANY, scopedCompanyId, UserStatus.ACTIVE);
        fixtures.assignRole(pay, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        // A verified rule version set for configuration to reference (test placeholder).
        ruleVersionSetId = fixtures.insertRuleVersionSet("VERIFIED");
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

    /** Create the single v0 company + legal entity via the approved API. */
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

    private String configBody(String pfApplicability, String pfRegStatus, String pfRegNumber,
                              String ptState, UUID ruleSet) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"effectiveFrom\":\"2026-04-01\",");
        sb.append("\"pfApplicability\":\"").append(pfApplicability).append("\",");
        if (pfRegStatus != null) {
            sb.append("\"pfRegistrationStatus\":\"").append(pfRegStatus).append("\",");
        }
        if (pfRegNumber != null) {
            sb.append("\"pfRegistrationNumber\":\"").append(pfRegNumber).append("\",");
        }
        if (ptState != null) {
            sb.append("\"ptState\":\"").append(ptState).append("\",");
        }
        sb.append("\"tdsPolicy\":\"NEW_REGIME_AUTOMATIC_V0\",");
        sb.append("\"ruleVersionSetId\":\"").append(ruleSet).append("\"}");
        return sb.toString();
    }

    // --- Happy path: PF applicable + registered + supported PT -----------
    @Test
    void upsertConfigurationCreates200AndAudited() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        auditLogRepository.deleteAll();

        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("YES", "REGISTERED", "MHBAN1234567", "Maharashtra",
                                ruleVersionSetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.pfApplicability").value("YES"))
                .andExpect(jsonPath("$.pfRegistrationStatus").value("REGISTERED"))
                .andExpect(jsonPath("$.ptState").value("Maharashtra"))
                .andExpect(jsonPath("$.tdsPolicy").value("NEW_REGIME_AUTOMATIC_V0"))
                .andExpect(jsonPath("$.ruleVersionSetId").value(ruleVersionSetId.toString()));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.STATUTORY_CONFIG_CREATED.equals(a.getAction())
                        && "SUCCESS".equals(a.getOutcome()))).isTrue();
    }

    @Test
    void getConfigurationReturns200AfterUpsert() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("NO", null, null, null, ruleVersionSetId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/statutory/configuration").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfApplicability").value("NO"));
    }

    @Test
    void getConfigurationBeforeUpsertIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(get("/api/v1/statutory/configuration").header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- Explicit applicability states -----------------------------------
    @Test
    void unconfirmedApplicabilityIsStoredExplicitly() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("UNCONFIRMED", null, null, null, ruleVersionSetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfApplicability").value("UNCONFIRMED"));
    }

    @Test
    void updateConfigurationInPlaceAndAudits() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("UNCONFIRMED", null, null, null, ruleVersionSetId)))
                .andExpect(status().isOk());
        auditLogRepository.deleteAll();

        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("YES", "REGISTERED", null, "Karnataka", ruleVersionSetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfApplicability").value("YES"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.STATUTORY_CONFIG_UPDATED.equals(a.getAction()))).isTrue();
    }

    // --- PF registration-state validation --------------------------------
    @Test
    void pfApplicableWithoutRegistrationStatusIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("YES", null, null, "Maharashtra", ruleVersionSetId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void pfNotApplicableWithRegistrationDetailsIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("NO", "REGISTERED", null, null, ruleVersionSetId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // --- PT state support -------------------------------------------------
    @Test
    void unsupportedPtStateIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("NO", null, null, "Gujarat", ruleVersionSetId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }

    // --- Invalid enums / structural validation ---------------------------
    @Test
    void invalidPfApplicabilityEnumIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("MAYBE", null, null, null, ruleVersionSetId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidTdsPolicyIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"pfApplicability\":\"NO\","
                + "\"tdsPolicy\":\"OLD_REGIME\",\"ruleVersionSetId\":\"" + ruleVersionSetId + "\"}";
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Rule version reference ------------------------------------------
    @Test
    void unknownRuleVersionSetIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("NO", null, null, null, UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // --- Effective-date validation ---------------------------------------
    @Test
    void effectiveToBeforeEffectiveFromIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompanyAndLegalEntity(auth);
        String body = "{\"effectiveFrom\":\"2026-04-01\",\"effectiveTo\":\"2026-03-01\","
                + "\"pfApplicability\":\"NO\",\"tdsPolicy\":\"NEW_REGIME_AUTOMATIC_V0\","
                + "\"ruleVersionSetId\":\"" + ruleVersionSetId + "\"}";
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // --- No legal entity yet ---------------------------------------------
    @Test
    void upsertWithoutLegalEntityIs404() throws Exception {
        String auth = bearer("superadmin");
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("NO", null, null, null, ruleVersionSetId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- Authorization ----------------------------------------------------
    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/configuration"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void payrollAdminWithoutCompanyAdminIs403() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/configuration").header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void payrollAdminCannotUpsertConfiguration403() throws Exception {
        String admin = bearer("superadmin");
        createCompanyAndLegalEntity(admin);
        mockMvc.perform(put("/api/v1/statutory/configuration").header("Authorization", bearer("payadmin"))
                        .contentType("application/json")
                        .content(configBody("NO", null, null, null, ruleVersionSetId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
