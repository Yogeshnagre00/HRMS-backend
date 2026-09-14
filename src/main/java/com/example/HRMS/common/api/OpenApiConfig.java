package com.example.HRMS.common.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the HRMS Payroll MVP backend.
 *
 * <p>springdoc automatically discovers the existing REST controllers and
 * generates the OpenAPI 3 document and Swagger UI. This bean only supplies
 * top-level API information (title, version, description). It intentionally does
 * not declare any operations — only endpoints that actually exist are
 * documented.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hrmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HRMS Payroll MVP API")
                        .version("v0.1")
                        .description("Backend API for the HRMS Payroll MVP. "
                                + "This documents only the endpoints implemented so far.")
                        .license(new License().name("Proprietary")));
    }
}
