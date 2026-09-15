package com.example.HRMS.payroll.controller;

import com.example.HRMS.common.api.PageResponse;
import com.example.HRMS.payroll.dto.PayrollCalculationDtos.CalculationSummaryResponse;
import com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultDetail;
import com.example.HRMS.payroll.dto.PayrollCalculationDtos.PayrollEmployeeResultSummary;
import com.example.HRMS.payroll.service.PayrollCalculationService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-statutory Payroll Calculation API (API spec §16/§17; task V2-008A).
 * Payroll-administration scoped ({@code payroll.admin}), company/legal-entity
 * isolated. Thin controller; the calculation pipeline, concurrency, atomicity,
 * NULL statutory/net handling, scope isolation and auditing live in
 * {@link PayrollCalculationService}.
 *
 * <p>Calculate/recalculate take no request body (the server calculates from the
 * run's persisted scope and current master data) and return {@code 200} with the
 * calculation summary DTO. The result reads never expose persistence entities or
 * bank details.
 */
@RestController
@RequestMapping("/api/v1/payroll/runs/{runId}")
@Tag(name = "Payroll Calculation",
        description = "Non-statutory (pre-statutory) payroll calculation and results")
public class PayrollCalculationController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PayrollCalculationService service;
    private final CurrentUser currentUser;

    public PayrollCalculationController(PayrollCalculationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "Calculate non-statutory payroll (DRAFT run only)",
            description = "Runs the non-statutory pipeline and moves the run "
                    + "DRAFT -> CALCULATED_PRE_STATUTORY. Statutory amounts and net pay are not "
                    + "computed (left NULL). Empty body; returns the calculation summary.")
    public ResponseEntity<CalculationSummaryResponse> calculate(@PathVariable UUID runId) {
        return ResponseEntity.ok(service.calculate(currentUser.require(), runId));
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "Recalculate non-statutory payroll (CALCULATED_PRE_STATUTORY run)",
            description = "Re-runs the non-statutory pipeline, replaces the result set atomically, "
                    + "advances the calculation version and stays CALCULATED_PRE_STATUTORY. "
                    + "Empty body; returns the calculation summary.")
    public ResponseEntity<CalculationSummaryResponse> recalculate(@PathVariable UUID runId) {
        return ResponseEntity.ok(service.recalculate(currentUser.require(), runId));
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "List employee payroll results (paginated)",
            description = "Employee result summaries for the run's current calculation, ordered by "
                    + "Employee ID. A DRAFT (not-yet-calculated) run returns an empty page.")
    public ResponseEntity<PageResponse<PayrollEmployeeResultSummary>> listEmployeeResults(
            @PathVariable UUID runId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        // Ordering is fixed in the repository query (business Employee ID, then
        // result id); pass an unsorted page request of just page + size.
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        return ResponseEntity.ok(PageResponse.from(
                service.listEmployeeResults(currentUser.require(), runId, pageable)));
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAuthority('payroll.admin')")
    @Operation(summary = "Read one employee's payroll calculation detail",
            description = "Header, earning lines and day-level breakdown for the run's current "
                    + "calculation. 404 if the run or the employee result is not in scope. "
                    + "Bank account details are never included.")
    public ResponseEntity<PayrollEmployeeResultDetail> getEmployeeResult(
            @PathVariable UUID runId, @PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getEmployeeResult(currentUser.require(), runId, employeeId));
    }
}
