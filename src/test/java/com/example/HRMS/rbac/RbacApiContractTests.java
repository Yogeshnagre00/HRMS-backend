package com.example.HRMS.rbac;

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
 * Verifies the reduced RBAC API contract after V0-003.3 cleanup: the three role
 * write operations are absent from the OpenAPI document and no controller mapping
 * exists for them, while the retained read/assignment operations remain.
 *
 * <p>Asserting the OpenAPI contract (rather than an arbitrary HTTP status for a
 * missing route) is the stronger check: it proves the operations were removed
 * from the application's API surface, independent of security-filter ordering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacApiContractTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode openApiPaths() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("paths");
    }

    private static boolean hasOperation(JsonNode paths, String path, String method) {
        JsonNode node = paths.get(path);
        return node != null && node.has(method);
    }

    @Test
    void removedRoleWriteOperationsAreAbsentFromOpenApi() throws Exception {
        JsonNode paths = openApiPaths();

        // POST /roles removed.
        assertThat(hasOperation(paths, "/api/v1/roles", "post"))
                .as("POST /api/v1/roles must be removed").isFalse();
        // PUT /roles/{roleId} removed.
        assertThat(hasOperation(paths, "/api/v1/roles/{roleId}", "put"))
                .as("PUT /api/v1/roles/{roleId} must be removed").isFalse();
        // PUT /roles/{roleId}/permissions removed (entire path gone).
        assertThat(paths.has("/api/v1/roles/{roleId}/permissions"))
                .as("PUT /api/v1/roles/{roleId}/permissions must be removed").isFalse();
    }

    @Test
    void retainedRbacOperationsRemainInOpenApi() throws Exception {
        JsonNode paths = openApiPaths();

        assertThat(hasOperation(paths, "/api/v1/roles", "get")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/roles/{roleId}", "get")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/permissions", "get")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/users/{userId}/roles", "get")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/users/{userId}/roles", "put")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/authorization/me", "get")).isTrue();
    }

    @Test
    void authenticationOperationsRemainInOpenApi() throws Exception {
        JsonNode paths = openApiPaths();

        assertThat(hasOperation(paths, "/api/v1/auth/login", "post")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/auth/logout", "post")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/auth/mfa/verify", "post")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/auth/me", "get")).isTrue();
        assertThat(hasOperation(paths, "/api/v1/auth/password/change", "post")).isTrue();
    }
}
