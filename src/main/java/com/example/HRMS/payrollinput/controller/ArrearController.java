package com.example.HRMS.payrollinput.controller;

import com.example.HRMS.common.api.PageResponse;
import com.example.HRMS.payrollinput.dto.PayrollInputDtos.ArrearResponse;
import com.example.HRMS.payrollinput.dto.PayrollInputDtos.CreateArrearRequest;
import com.example.HRMS.payrollinput.service.PayrollInputService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Arrear API (API spec 14.7; task V2-008A.6). Company-scoped input-management,
 * reusing the {@code company.admin} permission (V2-008A.2). Thin controller;
 * scope, DRAFT-only lifecycle, legal-entity compatibility, amount/period
 * validation and auditing live in {@link PayrollInputService}. Create and list
 * only — no update/delete in v0.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/arrears")
@Tag(name = "Arrear", description = "Independent payroll-period arrears")
public class ArrearController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PayrollInputService service;
    private final CurrentUser currentUser;

    public ArrearController(PayrollInputService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List an employee's arrears (paginated)")
    public ResponseEntity<PageResponse<ArrearResponse>> list(
            @PathVariable UUID employeeId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        return ResponseEntity.ok(PageResponse.from(
                service.listArrears(currentUser.require(), employeeId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Add an arrear (payroll run must be DRAFT)")
    public ResponseEntity<ArrearResponse> create(@PathVariable UUID employeeId,
            @Valid @RequestBody CreateArrearRequest request) {
        ArrearResponse created = service.createArrear(currentUser.require(), employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
