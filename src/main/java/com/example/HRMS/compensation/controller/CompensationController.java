package com.example.HRMS.compensation.controller;

import com.example.HRMS.compensation.dto.CompensationDtos.CompensationResponse;
import com.example.HRMS.compensation.service.CompensationService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-record Compensation read API (API §14): read one compensation record
 * by id, enforcing company scope through the record's employee. Company-scoped,
 * reusing {@code company.admin}. Read-only; no PUT/PATCH/DELETE.
 */
@RestController
@RequestMapping("/api/v1/compensation")
@Tag(name = "Compensation", description = "Effective-dated employee monthly compensation")
public class CompensationController {

    private final CompensationService service;
    private final CurrentUser currentUser;

    public CompensationController(CompensationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read one compensation record")
    public ResponseEntity<CompensationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(currentUser.require(), id));
    }
}
