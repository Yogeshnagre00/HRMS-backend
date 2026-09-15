package com.example.HRMS.workcalendar.controller;

import com.example.HRMS.common.api.PageResponse;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.CreateWorkCalendarRequest;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.UpdateWorkCalendarRequest;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.WorkCalendarAssignmentResponse;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.WorkCalendarResponse;
import com.example.HRMS.workcalendar.service.WorkCalendarService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Work Calendar API (API spec 9; task V2-008A.3). Company-scoped input-management
 * module, reusing the {@code company.admin} permission (V2-008A.2 decision).
 * Thin controller; scope isolation, v0-pattern validation, effective-date
 * overlap detection and auditing live in {@link WorkCalendarService}.
 */
@RestController
@RequestMapping("/api/v1/work-calendars")
@Tag(name = "Work Calendar", description = "Work calendar configuration and assignments")
public class WorkCalendarController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final WorkCalendarService service;
    private final CurrentUser currentUser;

    public WorkCalendarController(WorkCalendarService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List work calendars (paginated)")
    public ResponseEntity<PageResponse<WorkCalendarResponse>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        // Deterministic ordering: effective_from ascending, then id for stable ties.
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("effectiveFrom"), Sort.Order.asc("id")));
        return ResponseEntity.ok(PageResponse.from(service.listCalendars(currentUser.require(),
                pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create a work calendar")
    public ResponseEntity<WorkCalendarResponse> create(
            @Valid @RequestBody CreateWorkCalendarRequest request) {
        WorkCalendarResponse created = service.createCalendar(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read a work calendar by id")
    public ResponseEntity<WorkCalendarResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getCalendar(currentUser.require(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create/update effective work calendar state")
    public ResponseEntity<WorkCalendarResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateWorkCalendarRequest request) {
        return ResponseEntity.ok(service.updateCalendar(currentUser.require(), id, request));
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read employee assignments for a work calendar")
    public ResponseEntity<List<WorkCalendarAssignmentResponse>> assignments(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listAssignments(currentUser.require(), id));
    }
}
