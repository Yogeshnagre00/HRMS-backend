package com.example.HRMS.statutory.controller;

import com.example.HRMS.security.core.CurrentUser;
import com.example.HRMS.statutory.dto.StatutoryDtos.StatutoryConfigurationResponse;
import com.example.HRMS.statutory.dto.StatutoryDtos.UpsertStatutoryConfigurationRequest;
import com.example.HRMS.statutory.service.StatutoryConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statutory Configuration API (API spec §8). Company-scoped configuration, so it
 * reuses the {@code company.admin} permission. Thin controller; applicability
 * rules, PT-state support, PF-registration rules and auditing live in
 * {@link StatutoryConfigurationService}.
 */
@RestController
@RequestMapping("/api/v1/statutory/configuration")
@Tag(name = "Statutory Configuration",
        description = "Explicit PF/PT/TDS applicability and policy for the legal entity")
public class StatutoryConfigurationController {

    private final StatutoryConfigurationService service;
    private final CurrentUser currentUser;

    public StatutoryConfigurationController(StatutoryConfigurationService service,
                                            CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read effective statutory configuration")
    public ResponseEntity<StatutoryConfigurationResponse> getConfiguration() {
        return ResponseEntity.ok(service.getConfiguration());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create or update the effective statutory configuration",
            description = "PF/PT/TDS applicability must be explicit. UNCONFIRMED PF applicability "
                    + "is a stored state, never a silent default. An unsupported PT state is "
                    + "rejected. References a verified rule version set by id.")
    public ResponseEntity<StatutoryConfigurationResponse> upsertConfiguration(
            @Valid @RequestBody UpsertStatutoryConfigurationRequest request) {
        return ResponseEntity.ok(service.upsertConfiguration(currentUser.require(), request));
    }
}
