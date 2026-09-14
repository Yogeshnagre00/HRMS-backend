package com.example.HRMS.company;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * V0-004 Legal Entity API tests: create/list/get/update, single-active-entity
 * (409), missing company/entity (404), validation (400), authorization, audit,
 * and error contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalEntityApiTests {

    private static final String VALID_LE = "{\"legalName\":\"Acme India Pvt Ltd\","
            + "\"countryCode\":\"IN\",\"pan\":\"AAAAA0000A\","
            + "\"financialYearStart\":\"2025-04-01\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RbacTestFixtures fixtures;
    @Autowired
    private AuditLogRepository auditLogRepository;

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

    private void createCompany(String auth) throws Exception {
        mockMvc.perform(post("/api/v1/company").header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Acme Corp\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createLegalEntity201AndAudited() throws Exception {
        String auth = bearer("superadmin");
        createCompany(auth);
        auditLogRepository.deleteAll();
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json").content(VALID_LE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.legalName").value("Acme India Pvt Ltd"))
                .andExpect(jsonPath("$.countryCode").value("IN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.LEGAL_ENTITY_CREATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void secondLegalEntityIs409() throws Exception {
        String auth = bearer("superadmin");
        createCompany(auth);
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json").content(VALID_LE))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json").content(VALID_LE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createLegalEntityWithoutCompanyIs404() throws Exception {
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", bearer("superadmin"))
                        .contentType("application/json").content(VALID_LE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void listAndGetLegalEntity() throws Exception {
        String auth = bearer("superadmin");
        createCompany(auth);
        String created = mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json").content(VALID_LE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asString();

        JsonNode list = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/legal-entities").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/legal-entities/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pan").value("AAAAA0000A"));
    }

    @Test
    void getMissingLegalEntityIs404() throws Exception {
        String auth = bearer("superadmin");
        createCompany(auth);
        mockMvc.perform(get("/api/v1/legal-entities/" + UUID.randomUUID())
                        .header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateLegalEntitySucceedsAndAudits() throws Exception {
        String auth = bearer("superadmin");
        createCompany(auth);
        String created = mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json").content(VALID_LE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asString();
        auditLogRepository.deleteAll();

        mockMvc.perform(put("/api/v1/legal-entities/" + id).header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"legalName\":\"Acme India Renamed\",\"countryCode\":\"IN\","
                                + "\"pan\":\"AAAAA0000A\",\"financialYearStart\":\"2025-04-01\","
                                + "\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Acme India Renamed"));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> AuditActions.LEGAL_ENTITY_UPDATED.equals(a.getAction()))).isTrue();
    }

    @Test
    void invalidLegalEntityRequestIs400() throws Exception {
        String auth = bearer("superadmin");
        createCompany(auth);
        // Invalid PAN format.
        mockMvc.perform(post("/api/v1/legal-entities").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"legalName\":\"X\",\"countryCode\":\"IN\","
                                + "\"pan\":\"bad\",\"financialYearStart\":\"2025-04-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedLegalEntityAccessIs401() throws Exception {
        mockMvc.perform(get("/api/v1/legal-entities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
