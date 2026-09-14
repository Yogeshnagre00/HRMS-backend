package com.example.HRMS.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin, versioned application liveness endpoint.
 *
 * <p>Deep infrastructure health (datasource, Flyway, disk, etc.) is provided by Spring
 * Boot Actuator at {@code /actuator/health} and its {@code readiness}/{@code liveness}
 * probe groups. This controller exposes a stable, dependency-free liveness signal under
 * the {@code /api/v1} base defined by the API conventions so callers have a simple,
 * predictable check without coupling to actuator paths.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Application liveness endpoint")
public class HealthController {

    @GetMapping
    @Operation(summary = "Application liveness",
            description = "Returns UP when the application is running.")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "hrms-backend",
                "timestamp", OffsetDateTime.now().toString());
    }
}
