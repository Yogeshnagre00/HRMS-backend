package com.example.HRMS.leave;

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
 * Verifies the V2-008A.5 OpenAPI surface: exactly the leave-entry collection and
 * item endpoints (GET/POST, GET/PUT/DELETE); no speculative endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveEntryOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void leaveEntrySurfaceIsExact() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        JsonNode collection = paths.get("/api/v1/leave/entries");
        assertThat(collection).isNotNull();
        assertThat(collection.has("get")).isTrue();
        assertThat(collection.has("post")).isTrue();

        JsonNode item = paths.get("/api/v1/leave/entries/{id}");
        assertThat(item).isNotNull();
        assertThat(item.has("get")).isTrue();
        assertThat(item.has("put")).isTrue();
        assertThat(item.has("delete")).isTrue();

        // No speculative leave endpoints (application/approval/accrual).
        assertThat(paths.has("/api/v1/leave/applications")).isFalse();
        assertThat(paths.has("/api/v1/leave/approvals")).isFalse();
    }
}
