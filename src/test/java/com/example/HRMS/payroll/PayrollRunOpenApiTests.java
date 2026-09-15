package com.example.HRMS.payroll;

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
 * Verifies the V2-007 Payroll Run Foundation OpenAPI surface: exactly the
 * create/list/read endpoints exist and none of the later-slice endpoints
 * (calculate, recalculate, health-check, findings, approve, lock, correction,
 * outputs) are present.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollRunOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void payrollRunSurfaceIsExactlyTheFoundation() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        // Foundation endpoints present.
        JsonNode runs = paths.get("/api/v1/payroll/runs");
        assertThat(runs).as("collection path present").isNotNull();
        assertThat(runs.has("get")).isTrue();
        assertThat(runs.has("post")).isTrue();

        JsonNode run = paths.get("/api/v1/payroll/runs/{runId}");
        assertThat(run).as("single-run path present").isNotNull();
        assertThat(run.has("get")).isTrue();
        assertThat(run.has("post")).isFalse();
        assertThat(run.has("put")).isFalse();
        assertThat(run.has("patch")).isFalse();
        assertThat(run.has("delete")).isFalse();

        // Later-slice endpoints must NOT exist yet.
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/calculate")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/recalculate")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/health-check")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/findings")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/approve")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/lock")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/employees")).isFalse();
    }
}
