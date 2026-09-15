package com.example.HRMS.attendance.controller;

import com.example.HRMS.attendance.dto.AttendanceExceptionDtos.AttendanceExceptionResponse;
import com.example.HRMS.attendance.dto.AttendanceExceptionDtos.UpsertAttendanceExceptionRequest;
import com.example.HRMS.attendance.service.AttendanceExceptionService;
import com.example.HRMS.common.api.PageResponse;
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
 * Attendance Exception API (API spec 12; task V2-008A.4). Company-scoped
 * input-management module, reusing the {@code company.admin} permission
 * (V2-008A.2 decision). Thin controller; scope isolation, employment-period and
 * type/quantity validation, duplicate detection and auditing live in
 * {@link AttendanceExceptionService}. Attendance exceptions are payroll source
 * inputs only; no payroll calculation occurs here.
 */
@RestController
@RequestMapping("/api/v1/attendance/exceptions")
@Tag(name = "Attendance Exception", description = "Admin-entered attendance exceptions")
public class AttendanceExceptionController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AttendanceExceptionService service;
    private final CurrentUser currentUser;

    public AttendanceExceptionController(AttendanceExceptionService service,
                                         CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List attendance exceptions for an employee (paginated)")
    public ResponseEntity<PageResponse<AttendanceExceptionResponse>> list(
            @RequestParam(name = "employeeId") UUID employeeId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        // Deterministic ordering: attendance_date ascending, then id for stable ties.
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("attendanceDate"), Sort.Order.asc("id")));
        return ResponseEntity.ok(PageResponse.from(
                service.listForEmployee(currentUser.require(), employeeId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create an attendance exception")
    public ResponseEntity<AttendanceExceptionResponse> create(
            @Valid @RequestBody UpsertAttendanceExceptionRequest request) {
        AttendanceExceptionResponse created = service.create(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read an attendance exception by id")
    public ResponseEntity<AttendanceExceptionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(currentUser.require(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Update an attendance exception")
    public ResponseEntity<AttendanceExceptionResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpsertAttendanceExceptionRequest request) {
        return ResponseEntity.ok(service.update(currentUser.require(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Delete an attendance exception")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
