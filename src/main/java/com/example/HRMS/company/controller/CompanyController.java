package com.example.HRMS.company.controller;

import com.example.HRMS.company.dto.CompanyDtos.CompanyResponse;
import com.example.HRMS.company.dto.CompanyDtos.CreateCompanyRequest;
import com.example.HRMS.company.dto.CompanyDtos.UpdateCompanyRequest;
import com.example.HRMS.company.service.CompanyService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company API (API spec section 7). v0 is single-company, so the contract is
 * singular ({@code /api/v1/company} — no id, no list, no pagination). Thin
 * controller; all logic and cardinality enforcement live in {@link CompanyService}.
 */
@RestController
@RequestMapping("/api/v1/company")
@Tag(name = "Company", description = "Company / tenant management (single company in v0)")
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUser currentUser;

    public CompanyController(CompanyService companyService, CurrentUser currentUser) {
        this.companyService = companyService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read company")
    public ResponseEntity<CompanyResponse> getCompany() {
        return ResponseEntity.ok(companyService.getCompany());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create company",
            description = "Creates the single v0 company. A second active company is rejected (409).")
    public ResponseEntity<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse created = companyService.createCompany(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Update company")
    public ResponseEntity<CompanyResponse> updateCompany(
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.updateCompany(currentUser.require(), request));
    }
}
