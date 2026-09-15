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
 * Verifies the payroll-run OpenAPI surface after V2-008A: the V2-007 Foundation
 * (create/list/read) plus the V2-008A non-statutory calculation endpoints
 * (calculate, recalculate, employee-result list + detail). Still-later-slice
 * endpoints (health-check, findings, approve, lock, correction, outputs) must
 * remain absent.
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

        // V2-008A non-statutory calculation endpoints present.
        JsonNode calculate = paths.get("/api/v1/payroll/runs/{runId}/calculate");
        assertThat(calculate).as("calculate path present").isNotNull();
        assertThat(calculate.has("post")).isTrue();

        JsonNode recalculate = paths.get("/api/v1/payroll/runs/{runId}/recalculate");
        assertThat(recalculate).as("recalculate path present").isNotNull();
        assertThat(recalculate.has("post")).isTrue();

        JsonNode employees = paths.get("/api/v1/payroll/runs/{runId}/employees");
        assertThat(employees).as("employee-results path present").isNotNull();
        assertThat(employees.has("get")).isTrue();

        JsonNode employee = paths.get("/api/v1/payroll/runs/{runId}/employees/{employeeId}");
        assertThat(employee).as("employee-result detail path present").isNotNull();
        assertThat(employee.has("get")).isTrue();

        // Still-later-slice endpoints must NOT exist yet.
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/health-check")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/findings")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/approve")).isFalse();
        assertThat(paths.has("/api/v1/payroll/runs/{runId}/lock")).isFalse();
    }
}
