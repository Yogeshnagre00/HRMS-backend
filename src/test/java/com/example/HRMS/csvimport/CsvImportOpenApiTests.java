package com.example.HRMS.csvimport;

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
 * Verifies the V2-006 OpenAPI surface: the confirm endpoint exists as POST only,
 * the V2-005 GET session endpoint is unchanged, and no other import endpoints
 * (apply/commit, or PUT/PATCH/DELETE confirmation) were introduced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsvImportOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void confirmEndpointExistsAndSurfaceIsExact() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        // Confirm endpoint: POST only.
        JsonNode confirm = paths.get("/api/v1/employees/imports/{importId}/confirm");
        assertThat(confirm).as("confirm path present").isNotNull();
        assertThat(confirm.has("post")).isTrue();
        assertThat(confirm.has("put")).isFalse();
        assertThat(confirm.has("patch")).isFalse();
        assertThat(confirm.has("delete")).isFalse();
        assertThat(confirm.has("get")).isFalse();

        // V2-005 GET session endpoint unchanged (GET present).
        JsonNode session = paths.get("/api/v1/employees/imports/{importId}");
        assertThat(session).isNotNull();
        assertThat(session.has("get")).isTrue();

        // Upload endpoint unchanged (POST present).
        JsonNode uploads = paths.get("/api/v1/employees/imports");
        assertThat(uploads).isNotNull();
        assertThat(uploads.has("post")).isTrue();

        // No invented apply/commit endpoints.
        assertThat(paths.has("/api/v1/employees/imports/{importId}/apply")).isFalse();
        assertThat(paths.has("/api/v1/employees/imports/{importId}/commit")).isFalse();
    }
}
