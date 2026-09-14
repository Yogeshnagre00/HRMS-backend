package com.example.HRMS.statutory.controller;

import com.example.HRMS.statutory.dto.StatutoryDtos.RuleVersionSetResponse;
import com.example.HRMS.statutory.service.RuleVersionSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statutory Rule Version Set API (API spec §8) — read-only in v0.
 *
 * <p>Rule version sets are verified release inputs; the API lists and reads them
 * so configuration/payroll can reference a specific version. There is no create
 * or update endpoint (the API layer must not invent statutory values). Reuses
 * the {@code company.admin} permission (company-scoped configuration territory).
 */
@RestController
@RequestMapping("/api/v1/statutory/rule-versions")
@Tag(name = "Statutory Rule Versions",
        description = "Read-only verified statutory rule version sets")
public class RuleVersionSetController {

    private final RuleVersionSetService service;

    public RuleVersionSetController(RuleVersionSetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List available statutory rule version sets")
    public ResponseEntity<List<RuleVersionSetResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read rule version set metadata")
    public ResponseEntity<RuleVersionSetResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }
}
