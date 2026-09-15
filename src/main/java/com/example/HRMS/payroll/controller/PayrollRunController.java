package com.example.HRMS.payroll.controller;

import com.example.HRMS.common.api.PageResponse;
import com.example.HRMS.payroll.dto.PayrollRunDtos.CreatePayrollRunRequest;
import com.example.HRMS.payroll.dto.PayrollRunDtos.PayrollRunResponse;
import com.example.HRMS.payroll.service.PayrollRunService;
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
 * Payroll Run API (API spec 15; task V2-007 Payroll Run Foundation).
 * Payroll-administration scoped, reusing the existing {@code payroll.admin}
 * permission (PAYROLL_ADMIN). Thin controller; period validation, scope
 * isolation, financial-year derivation, rule-version validation, duplicate-run
 * protection and auditing live in {@link PayrollRunService}.
 *
 * <p>This slice exposes only run creation, a paginated list and single read.
 * Calculation, Health Check, approval, lock, correction and outputs are later
 * slices and are intentionally not exposed here.
 */
@RestController
@RequestMapping("/api/v1/payroll/runs")
@Tag(name = "Payroll Run", description = "Monthly payroll run foundation (create/list/read)")
public class PayrollRunController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PayrollRunService payrollRunService;
    private final CurrentUser currentUser;

    public PayrollRunController(PayrollRunService payrollRunService, CurrentUser currentUser) {
        this.payrollRunService = payrollRunService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "List payroll runs (paginated)")
    public ResponseEntity<PageResponse<PayrollRunResponse>> listRuns(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        // Deterministic ordering: newest payroll month first, then surrogate id
        // for stable ties (safe, fixed sort fields only — no client-supplied sort).
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("payrollMonth"), Sort.Order.asc("id")));
        return ResponseEntity.ok(PageResponse.from(
                payrollRunService.listRuns(currentUser.require(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "Create a monthly payroll run",
            description = "Creates a DRAFT payroll run for the caller's legal entity and the "
                    + "given payroll month (first-of-month). One primary run per legal entity + "
                    + "payroll month (409 on duplicate). No payroll calculation occurs.")
    public ResponseEntity<PayrollRunResponse> createRun(
            @Valid @RequestBody CreatePayrollRunRequest request) {
        PayrollRunResponse created = payrollRunService.createRun(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "Read a payroll run and its lifecycle state")
    public ResponseEntity<PayrollRunResponse> getRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(payrollRunService.getRun(currentUser.require(), runId));
    }
}
