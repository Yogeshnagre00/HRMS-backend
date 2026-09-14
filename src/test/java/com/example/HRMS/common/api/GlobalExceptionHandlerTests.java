package com.example.HRMS.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the common error/validation mechanism produces the shared {@link ApiError}
 * contract for request-validation failures. A test-only controller triggers a body
 * validation failure that {@link GlobalExceptionHandler} must translate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationFailureUsesCommonErrorContract() throws Exception {
        mockMvc.perform(post("/test-support/validate")
                        .with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                // Top-level message comes from the centralized ApiMessages source.
                .andExpect(jsonPath("$.message").value(ApiMessages.VALIDATION_FAILED_FIELDS))
                .andExpect(jsonPath("$.path").value("/test-support/validate"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").exists());
    }

    @TestConfiguration
    static class TestControllerConfig {
        @Bean
        TestValidationController testValidationController() {
            return new TestValidationController();
        }
    }

    @RestController
    @RequestMapping("/test-support")
    static class TestValidationController {
        @PostMapping("/validate")
        String validate(@Valid @RequestBody Payload payload) {
            return "ok";
        }

        record Payload(@NotBlank String name) {
        }
    }
}
