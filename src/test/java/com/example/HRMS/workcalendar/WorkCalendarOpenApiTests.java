package com.example.HRMS.workcalendar;

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
 * Verifies the V2-008A.3 OpenAPI surface: exactly the work-calendar and employee
 * assignment endpoints exist, with no speculative endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkCalendarOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void workCalendarSurfaceIsExact() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        JsonNode collection = paths.get("/api/v1/work-calendars");
        assertThat(collection).isNotNull();
        assertThat(collection.has("get")).isTrue();
        assertThat(collection.has("post")).isTrue();

        JsonNode single = paths.get("/api/v1/work-calendars/{id}");
        assertThat(single).isNotNull();
        assertThat(single.has("get")).isTrue();
        assertThat(single.has("put")).isTrue();
        assertThat(single.has("delete")).isFalse();

        JsonNode assignments = paths.get("/api/v1/work-calendars/{id}/assignments");
        assertThat(assignments).isNotNull();
        assertThat(assignments.has("get")).isTrue();

        JsonNode employeeAssign = paths.get("/api/v1/employees/{employeeId}/work-calendar");
        assertThat(employeeAssign).isNotNull();
        assertThat(employeeAssign.has("put")).isTrue();
        assertThat(employeeAssign.has("post")).isFalse();
        assertThat(employeeAssign.has("delete")).isFalse();
    }
}
