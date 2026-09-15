package com.example.HRMS.leave.controller;

import com.example.HRMS.leave.dto.LeaveBalanceDtos.LeaveBalanceResponse;
import com.example.HRMS.leave.dto.LeaveBalanceDtos.SetLeaveBalanceRequest;
import com.example.HRMS.leave.service.LeaveBalanceService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee Leave Balance API (API spec 13): {@code GET} and {@code PUT} on the
 * employee-associated current-FY paid-leave balance resource. Company-scoped,
 * reusing the {@code company.admin} permission. Thin controller; scope isolation,
 * server-side FY derivation, the balance formula and auditing live in
 * {@link LeaveBalanceService}. PUT is the authoritative set/update method; there
 * is intentionally no PATCH/POST/DELETE.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/leave-balance")
@Tag(name = "Employee Leave Balance",
        description = "Employee current-FY opening/current paid-leave balance")
public class LeaveBalanceController {

    private final LeaveBalanceService service;
    private final CurrentUser currentUser;

    public LeaveBalanceController(LeaveBalanceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read the employee's current-FY paid-leave balance")
    public ResponseEntity<LeaveBalanceResponse> getLeaveBalance(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getLeaveBalance(currentUser.require(), employeeId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Set (create or replace) the employee's current-FY paid-leave balance",
            description = "Full representation: opening balance, approved additions and used "
                    + "quantity. availableBalance is derived server-side; the financial year is "
                    + "server-derived and never client-supplied.")
    public ResponseEntity<LeaveBalanceResponse> setLeaveBalance(@PathVariable UUID employeeId,
            @Valid @RequestBody SetLeaveBalanceRequest request) {
        return ResponseEntity.ok(
                service.setLeaveBalance(currentUser.require(), employeeId, request));
    }
}
