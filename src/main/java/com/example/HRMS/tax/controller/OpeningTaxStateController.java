package com.example.HRMS.tax.controller;

import com.example.HRMS.security.core.CurrentUser;
import com.example.HRMS.tax.dto.OpeningTaxStateDtos.OpeningTaxStateResponse;
import com.example.HRMS.tax.dto.OpeningTaxStateDtos.PatchOpeningTaxStateRequest;
import com.example.HRMS.tax.service.OpeningTaxStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee Opening Tax State API (API spec 10.1): {@code GET} and {@code PATCH}
 * on the employee-associated current-FY resource. Company-scoped, reusing the
 * {@code company.admin} permission. Thin controller; scope isolation, server-side
 * FY derivation, source assignment and auditing live in
 * {@link OpeningTaxStateService}. There is intentionally no PUT/POST/DELETE.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/opening-tax-state")
@Tag(name = "Employee Opening Tax State",
        description = "Employee current-FY opening tax state (taxable income + TDS already deducted)")
public class OpeningTaxStateController {

    private final OpeningTaxStateService service;
    private final CurrentUser currentUser;

    public OpeningTaxStateController(OpeningTaxStateService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read the employee's current-FY opening tax state")
    public ResponseEntity<OpeningTaxStateResponse> getOpeningTaxState(
            @PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getOpeningTaxState(currentUser.require(), employeeId));
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create or partially update the employee's current-FY opening tax state",
            description = "The financial year is server-derived and never client-supplied. "
                    + "Creates the current-FY state if absent; otherwise partially updates it.")
    public ResponseEntity<OpeningTaxStateResponse> patchOpeningTaxState(
            @PathVariable UUID employeeId,
            @Valid @RequestBody PatchOpeningTaxStateRequest request) {
        return ResponseEntity.ok(
                service.patchOpeningTaxState(currentUser.require(), employeeId, request));
    }
}
