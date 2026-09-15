package com.example.HRMS.compensation.controller;

import com.example.HRMS.compensation.dto.CompensationDtos.CompensationRequest;
import com.example.HRMS.compensation.dto.CompensationDtos.CompensationResponse;
import com.example.HRMS.compensation.service.CompensationService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee-scoped Compensation API (API §14): list, create-first, and revise.
 * Company-scoped, reusing {@code company.admin}. Thin controller; scope
 * isolation, effective-date/overlap handling, revision atomicity and auditing
 * live in {@link CompensationService}. No PUT/PATCH/DELETE; no variable-earning
 * or arrear endpoints.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/compensation")
@Tag(name = "Compensation", description = "Effective-dated employee monthly compensation")
public class EmployeeCompensationController {

    private final CompensationService service;
    private final CurrentUser currentUser;

    public EmployeeCompensationController(CompensationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List effective-dated compensation records (newest first)")
    public ResponseEntity<List<CompensationResponse>> list(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.listForEmployee(currentUser.require(), employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create the initial compensation record",
            description = "Creates the first compensation record; 409 if one already exists "
                    + "(use a revision to change compensation).")
    public ResponseEntity<CompensationResponse> createFirst(@PathVariable UUID employeeId,
            @Valid @RequestBody CompensationRequest request) {
        CompensationResponse created =
                service.createFirst(currentUser.require(), employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/revisions")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create a salary revision",
            description = "Closes the current open-ended record (effective_to = new "
                    + "effectiveFrom - 1 day) and creates a new open-ended record, atomically.")
    public ResponseEntity<CompensationResponse> createRevision(@PathVariable UUID employeeId,
            @Valid @RequestBody CompensationRequest request) {
        CompensationResponse created =
                service.createRevision(currentUser.require(), employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
