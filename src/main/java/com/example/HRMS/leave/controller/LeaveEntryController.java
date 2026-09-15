package com.example.HRMS.leave.controller;

import com.example.HRMS.common.api.PageResponse;
import com.example.HRMS.leave.dto.LeaveEntryDtos.CreateLeaveEntryRequest;
import com.example.HRMS.leave.dto.LeaveEntryDtos.LeaveEntryResponse;
import com.example.HRMS.leave.dto.LeaveEntryDtos.UpdateLeaveEntryRequest;
import com.example.HRMS.leave.service.LeaveEntryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leave Entry API (API spec 13; task V2-008A.5). Company-scoped input-management
 * module, reusing the {@code company.admin} permission (V2-008A.2 decision).
 * Thin controller; scope isolation, employment-period/quantity validation,
 * duplicate + attendance-conflict detection, paid-leave balance consumption and
 * auditing live in {@link LeaveEntryService}.
 *
 * <p>The only v0 status transition is cancellation: {@code PUT} applies
 * {@code status: CANCELLED} and {@code DELETE} is cancellation. Identity fields
 * are not mutable (the authoritative contract defines no field-edit transition).
 */
@RestController
@RequestMapping("/api/v1/leave/entries")
@Tag(name = "Leave Entry", description = "Admin-entered leave entries")
public class LeaveEntryController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final LeaveEntryService service;
    private final CurrentUser currentUser;

    public LeaveEntryController(LeaveEntryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List leave entries for an employee (paginated)")
    public ResponseEntity<PageResponse<LeaveEntryResponse>> list(
            @RequestParam(name = "employeeId") UUID employeeId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        // Deterministic ordering: leave_date ascending, then id for stable ties.
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("leaveDate"), Sort.Order.asc("id")));
        return ResponseEntity.ok(PageResponse.from(
                service.listForEmployee(currentUser.require(), employeeId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create a leave entry")
    public ResponseEntity<LeaveEntryResponse> create(
            @Valid @RequestBody CreateLeaveEntryRequest request) {
        LeaveEntryResponse created = service.create(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read a leave entry by id")
    public ResponseEntity<LeaveEntryResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(currentUser.require(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Cancel a leave entry (status: CANCELLED)")
    public ResponseEntity<LeaveEntryResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveEntryRequest request) {
        // The only authoritative v0 transition is cancellation; the validated
        // request status is CANCELLED.
        return ResponseEntity.ok(service.cancel(currentUser.require(), id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Cancel a leave entry")
    public ResponseEntity<LeaveEntryResponse> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(currentUser.require(), id));
    }
}
