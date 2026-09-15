package com.example.HRMS.payrollinput;

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
 * Verifies the V2-008A.6 OpenAPI surface: earnings and arrears expose only
 * GET + POST (no PUT/PATCH/DELETE), and no speculative endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollInputOpenApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void payrollInputSurfaceIsExact() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");

        JsonNode earnings = paths.get("/api/v1/employees/{employeeId}/earnings");
        assertThat(earnings).isNotNull();
        assertThat(earnings.has("get")).isTrue();
        assertThat(earnings.has("post")).isTrue();
        assertThat(earnings.has("put")).isFalse();
        assertThat(earnings.has("patch")).isFalse();
        assertThat(earnings.has("delete")).isFalse();

        JsonNode arrears = paths.get("/api/v1/employees/{employeeId}/arrears");
        assertThat(arrears).isNotNull();
        assertThat(arrears.has("get")).isTrue();
        assertThat(arrears.has("post")).isTrue();
        assertThat(arrears.has("put")).isFalse();
        assertThat(arrears.has("patch")).isFalse();
        assertThat(arrears.has("delete")).isFalse();
    }
}
