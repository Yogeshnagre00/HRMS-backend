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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 2 statutory rule-value store tests: DRAFT create/read, verification gate,
 * immutability, overlap, authorization (SUPER_ADMIN via statutory.release only),
 * superseded readability, error codes and audit. No statutory numerical values
 * are asserted — payloads are structural JSON only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatutoryRuleReleaseApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RbacTestFixtures fixtures;
    @Autowired private AuditLogRepository auditLogRepository;

    private UUID versionSetId;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        UUID superId = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(superId, RbacTestFixtures.ROLE_SUPER_ADMIN);
        // A rule version set to attach rules to (VERIFIED header from V2-007 fixture).
        versionSetId = fixtures.insertRuleVersionSet("VERIFIED");
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

    private MvcResult createPfDraft(String auth, String from, String to, boolean withMetadata,
                                    String payload) throws Exception {
        return createPfDraftIn(auth, versionSetId, from, to, withMetadata, payload);
    }

    private MvcResult createPfDraftIn(String auth, UUID vsId, String from, String to,
                                      boolean withMetadata, String payload) throws Exception {
        StringBuilder b = new StringBuilder();
        b.append("{\"ruleVersionSetId\":\"").append(vsId).append("\",")
                .append("\"jurisdiction\":\"IN\",")
                .append("\"effectiveFrom\":\"").append(from).append("\"");
        if (to != null) {
            b.append(",\"effectiveTo\":\"").append(to).append("\"");
        }
        if (withMetadata) {
            b.append(",\"authority\":\"EPFO\",\"sourceDocument\":\"EPF Scheme\",")
                    .append("\"sourceUrl\":\"https://www.epfindia.gov.in\",")
                    .append("\"verificationDate\":\"2026-06-29\"");
        }
        if (payload != null) {
            // rulePayload is a raw JSON document carried as a JSON string field;
            // encode the payload as a JSON string literal.
            b.append(",\"rulePayload\":").append(objectMapper.writeValueAsString(payload));
        }
        b.append("}");
        return mockMvc.perform(post("/api/v1/statutory/rules/pf")
                        .header("Authorization", auth)
                        .contentType("application/json").content(b.toString()))
                .andReturn();
    }

    private UUID idOf(MvcResult r) throws Exception {
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("id").asString());
    }

    // ---- DRAFT create + read ----------------------------------------------

    @Test
    void createPfDraftSucceedsAndIsReadable() throws Exception {
        String auth = bearer("superadmin");
        auditLogRepository.deleteAll();
        MvcResult r = createPfDraft(auth, "2026-04-01", null, false, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("ruleType").asString()).isEqualTo("PF");
        assertThat(body.get("releaseStatus").asString()).isEqualTo("DRAFT");
        // Audited create.
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.STATUTORY_RULE_CREATED.equals(a.getAction()))).isTrue();
        // Readable via list.
        mockMvc.perform(get("/api/v1/statutory/rules?ruleVersionSetId=" + versionSetId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleType").value("PF"));
    }

    // ---- verification gate -------------------------------------------------

    @Test
    void draftWithoutMetadataOrPayloadCannotBeVerified() throws Exception {
        String auth = bearer("superadmin");
        UUID id = idOf(createPfDraft(auth, "2026-04-01", null, false, null));
        MvcResult r = mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/verify")
                        .header("Authorization", auth)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void draftWithMetadataAndPayloadCanBeVerified() throws Exception {
        String auth = bearer("superadmin");
        UUID id = idOf(createPfDraft(auth, "2026-04-01", null, true, "{\"employeeRate\":null}"));
        MvcResult r = mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/verify")
                        .header("Authorization", auth)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("releaseStatus").asString()).isEqualTo("VERIFIED");
        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.STATUTORY_RULE_VERIFIED.equals(a.getAction()))).isTrue();
    }

    @Test
    void malformedJsonPayloadRejectedAtCreate() throws Exception {
        String auth = bearer("superadmin");
        // A malformed (unparseable) JSON payload is rejected at create time. The
        // rulePayload field carries a raw JSON document; here it is an invalid
        // one (unterminated object), which must be rejected as 400 BAD_REQUEST.
        String body = "{\"ruleVersionSetId\":\"" + versionSetId + "\",\"jurisdiction\":\"IN\","
                + "\"effectiveFrom\":\"2026-04-01\",\"rulePayload\":\"{bad json\"}";
        MvcResult bad = mockMvc.perform(post("/api/v1/statutory/rules/pf")
                        .header("Authorization", auth)
                        .contentType("application/json").content(body)).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(bad.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("BAD_REQUEST");
    }

    // ---- immutability ------------------------------------------------------

    @Test
    void verifiedRuleCannotBeUpdatedOrReverified() throws Exception {
        String auth = bearer("superadmin");
        UUID id = idOf(createPfDraft(auth, "2026-04-01", null, true, "{\"a\":1}"));
        mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/verify")
                .header("Authorization", auth)).andExpect(status().isOk());
        // Update rejected (409, not DRAFT).
        String upd = "{\"effectiveFrom\":\"2026-04-01\"}";
        assertThat(mockMvc.perform(put("/api/v1/statutory/rules/pf/" + id)
                        .header("Authorization", auth)
                        .contentType("application/json").content(upd)).andReturn()
                .getResponse().getStatus()).isEqualTo(409);
        // Re-verify rejected (409, not DRAFT).
        assertThat(mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/verify")
                        .header("Authorization", auth)).andReturn()
                .getResponse().getStatus()).isEqualTo(409);
    }

    // ---- supersession + readability ---------------------------------------

    @Test
    void supersededRuleRemainsReadable() throws Exception {
        String auth = bearer("superadmin");
        UUID id = idOf(createPfDraft(auth, "2026-04-01", null, true, "{\"a\":1}"));
        mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/verify")
                .header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/supersede")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseStatus").value("SUPERSEDED"));
        // Still readable in the list.
        mockMvc.perform(get("/api/v1/statutory/rules?ruleVersionSetId=" + versionSetId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].releaseStatus").value("SUPERSEDED"));
    }

    @Test
    void draftCannotBeSuperseded() throws Exception {
        String auth = bearer("superadmin");
        UUID id = idOf(createPfDraft(auth, "2026-04-01", null, true, "{\"a\":1}"));
        assertThat(mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id + "/supersede")
                        .header("Authorization", auth)).andReturn()
                .getResponse().getStatus()).isEqualTo(409);
    }

    // ---- overlap -----------------------------------------------------------

    @Test
    void overlappingVerifiedPfRulesRejectedAdjacentAllowed() throws Exception {
        String auth = bearer("superadmin");
        // Each PF rule lives in its own version set (one PF rule per set); the
        // overlap invariant applies across VERIFIED rules for the same jurisdiction.
        UUID vs1 = fixtures.insertRuleVersionSet("VERIFIED");
        UUID vs2 = fixtures.insertRuleVersionSet("VERIFIED");
        UUID vs3 = fixtures.insertRuleVersionSet("VERIFIED");

        // First VERIFIED PF rule Apr-Jun 2026.
        UUID id1 = idOf(createPfDraftIn(auth, vs1, "2026-04-01", "2026-06-30", true, "{\"a\":1}"));
        mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id1 + "/verify")
                .header("Authorization", auth)).andExpect(status().isOk());
        // Overlapping (Jun-Aug) rejected on verify.
        UUID id2 = idOf(createPfDraftIn(auth, vs2, "2026-06-15", "2026-08-31", true, "{\"a\":1}"));
        assertThat(mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id2 + "/verify")
                        .header("Authorization", auth)).andReturn()
                .getResponse().getStatus()).isEqualTo(409);
        // Adjacent (Jul onwards) allowed.
        UUID id3 = idOf(createPfDraftIn(auth, vs3, "2026-07-01", null, true, "{\"a\":1}"));
        assertThat(mockMvc.perform(post("/api/v1/statutory/rules/pf/" + id3 + "/verify")
                        .header("Authorization", auth)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    // ---- effective-range validation ---------------------------------------

    @Test
    void invalidEffectiveRangeRejected() throws Exception {
        String auth = bearer("superadmin");
        MvcResult r = createPfDraft(auth, "2026-06-01", "2026-04-01", false, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    // ---- PT (state + periodicity) -----------------------------------------

    @Test
    void createPtDraftWithHalfYearlyTamilNadu() throws Exception {
        String auth = bearer("superadmin");
        String body = "{\"ruleVersionSetId\":\"" + versionSetId + "\",\"jurisdiction\":\"IN-TN\","
                + "\"ptState\":\"TAMIL_NADU\",\"localBody\":\"Greater Chennai Corporation\","
                + "\"periodicity\":\"HALF_YEARLY\",\"effectiveFrom\":\"2026-04-01\"}";
        MvcResult r = mockMvc.perform(post("/api/v1/statutory/rules/pt")
                        .header("Authorization", auth)
                        .contentType("application/json").content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode n = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(n.get("ptState").asString()).isEqualTo("TAMIL_NADU");
        assertThat(n.get("periodicity").asString()).isEqualTo("HALF_YEARLY");
        assertThat(n.get("localBody").asString()).isEqualTo("Greater Chennai Corporation");
    }

    @Test
    void unsupportedPtStateRejected() throws Exception {
        String auth = bearer("superadmin");
        String body = "{\"ruleVersionSetId\":\"" + versionSetId + "\",\"jurisdiction\":\"IN-XX\","
                + "\"ptState\":\"KERALA\",\"periodicity\":\"MONTHLY\","
                + "\"effectiveFrom\":\"2026-04-01\"}";
        MvcResult r = mockMvc.perform(post("/api/v1/statutory/rules/pt")
                        .header("Authorization", auth)
                        .contentType("application/json").content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString())
                .get("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    // ---- TDS ---------------------------------------------------------------

    @Test
    void createTdsDraftNewRegime() throws Exception {
        String auth = bearer("superadmin");
        String body = "{\"ruleVersionSetId\":\"" + versionSetId + "\",\"jurisdiction\":\"IN\","
                + "\"financialYear\":\"2026-27\",\"taxRegime\":\"NEW_REGIME_AUTOMATIC_V0\","
                + "\"effectiveFrom\":\"2026-04-01\"}";
        assertThat(mockMvc.perform(post("/api/v1/statutory/rules/tds")
                        .header("Authorization", auth)
                        .contentType("application/json").content(body)).andReturn()
                .getResponse().getStatus()).isEqualTo(201);
    }

    // ---- authorization -----------------------------------------------------

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/v1/statutory/rules?ruleVersionSetId=" + versionSetId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void companyAdminForbidden() throws Exception {
        UUID companyId = fixtures.insertCompany("Acme");
        UUID ca = fixtures.createUser("companyadmin", ScopeType.COMPANY, companyId,
                UserStatus.ACTIVE);
        fixtures.assignRole(ca, RbacTestFixtures.ROLE_COMPANY_ADMIN);
        mockMvc.perform(get("/api/v1/statutory/rules?ruleVersionSetId=" + versionSetId)
                        .header("Authorization", bearer("companyadmin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void payrollAdminForbidden() throws Exception {
        UUID companyId = fixtures.insertCompany("Acme");
        UUID pa = fixtures.createUser("payadmin", ScopeType.COMPANY, companyId, UserStatus.ACTIVE);
        fixtures.assignRole(pa, RbacTestFixtures.ROLE_PAYROLL_ADMIN);
        String body = "{\"ruleVersionSetId\":\"" + versionSetId + "\",\"jurisdiction\":\"IN\","
                + "\"effectiveFrom\":\"2026-04-01\"}";
        mockMvc.perform(post("/api/v1/statutory/rules/pf")
                        .header("Authorization", bearer("payadmin"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    // ---- unknown version set ----------------------------------------------

    @Test
    void unknownVersionSetIs404() throws Exception {
        String auth = bearer("superadmin");
        String body = "{\"ruleVersionSetId\":\"" + UUID.randomUUID() + "\",\"jurisdiction\":\"IN\","
                + "\"effectiveFrom\":\"2026-04-01\"}";
        MvcResult r = mockMvc.perform(post("/api/v1/statutory/rules/pf")
                        .header("Authorization", auth)
                        .contentType("application/json").content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }
}
