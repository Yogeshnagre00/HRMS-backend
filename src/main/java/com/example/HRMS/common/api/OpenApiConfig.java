package com.example.HRMS.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the HRMS Payroll MVP backend.
 *
 * <p>springdoc automatically discovers the implemented REST controllers and
 * generates the OpenAPI 3 document and serves the standard Swagger UI. This bean
 * supplies top-level API information, registers the JWT bearer security scheme,
 * and applies it as a global security requirement so Swagger UI shows the
 * "Authorize" button and sends {@code Authorization: Bearer <token>} on requests.
 * Public endpoints (login, health, API docs) remain accessible without a token;
 * that is enforced by the security filter chain, independent of this document.
 * It intentionally declares no operations — only endpoints that actually exist
 * are documented.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI hrmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HRMS Payroll MVP API")
                        .version("v0.1")
                        .description("Backend API for the HRMS Payroll MVP. "
                                + "This documents only the endpoints implemented so far. "
                                + "Call POST /api/v1/auth/login, then click Authorize and paste "
                                + "the accessToken to call protected endpoints.")
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste only the raw JWT access token (no 'Bearer ' prefix).")))
                // Global requirement -> Swagger UI renders the Authorize button and
                // sends the bearer token on every operation.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
