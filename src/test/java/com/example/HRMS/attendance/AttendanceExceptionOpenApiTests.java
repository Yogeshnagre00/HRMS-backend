package com.example.HRMS.attendance;

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
 * Verifies the V2-008A.4 OpenAPI surface: exactly the attendance-exception
 * collection and item endpoints (GET/POST, GET/PUT/DELETE); no speculative
 * endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceExceptionOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void attendanceSurfaceIsExact() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        JsonNode collection = paths.get("/api/v1/attendance/exceptions");
        assertThat(collection).isNotNull();
        assertThat(collection.has("get")).isTrue();
        assertThat(collection.has("post")).isTrue();

        JsonNode item = paths.get("/api/v1/attendance/exceptions/{id}");
        assertThat(item).isNotNull();
        assertThat(item.has("get")).isTrue();
        assertThat(item.has("put")).isTrue();
        assertThat(item.has("delete")).isTrue();

        // No speculative attendance endpoints.
        assertThat(paths.has("/api/v1/attendance/check-in")).isFalse();
        assertThat(paths.has("/api/v1/attendance/check-out")).isFalse();
    }
}
