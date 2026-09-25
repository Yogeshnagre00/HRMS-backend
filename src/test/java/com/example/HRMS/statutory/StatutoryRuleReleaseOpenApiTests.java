package com.example.HRMS.statutory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the Phase 2 statutory rule-release OpenAPI surface: the PF/PT/TDS
 * DRAFT/verify/supersede endpoints and the list endpoint exist; the existing
 * read-only rule-versions surface remains present.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatutoryRuleReleaseOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void statutoryRuleReleaseSurfacePresent() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        assertThat(paths.has("/api/v1/statutory/rules")).isTrue();
        assertThat(paths.get("/api/v1/statutory/rules").has("get")).isTrue();

        assertThat(paths.get("/api/v1/statutory/rules/pf").has("post")).isTrue();
        assertThat(paths.get("/api/v1/statutory/rules/pt").has("post")).isTrue();
        assertThat(paths.get("/api/v1/statutory/rules/tds").has("post")).isTrue();

        assertThat(paths.get("/api/v1/statutory/rules/pf/{id}").has("put")).isTrue();
        assertThat(paths.has("/api/v1/statutory/rules/pf/{id}/verify")).isTrue();
        assertThat(paths.has("/api/v1/statutory/rules/pf/{id}/supersede")).isTrue();

        // Existing read-only rule-version metadata surface remains.
        assertThat(paths.has("/api/v1/statutory/rule-versions")).isTrue();
    }
}
