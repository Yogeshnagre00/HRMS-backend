package com.example.HRMS.workcalendar.controller;

import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.AssignWorkCalendarRequest;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.WorkCalendarAssignmentResponse;
import com.example.HRMS.workcalendar.service.WorkCalendarService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee Work Calendar Assignment API (API spec 9; task V2-008A.3). The
 * employee-scoped assignment endpoint lives under the employee resource path.
 * Company-scoped, reusing {@code company.admin}. Thin controller; scope,
 * cross-legal-entity rejection and assignment overlap live in
 * {@link WorkCalendarService}.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/work-calendar")
@Tag(name = "Work Calendar", description = "Work calendar configuration and assignments")
public class EmployeeWorkCalendarController {

    private final WorkCalendarService service;
    private final CurrentUser currentUser;

    public EmployeeWorkCalendarController(WorkCalendarService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PutMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Assign an effective work calendar to an employee")
    public ResponseEntity<WorkCalendarAssignmentResponse> assign(
            @PathVariable UUID employeeId,
            @Valid @RequestBody AssignWorkCalendarRequest request) {
        return ResponseEntity.ok(service.assignToEmployee(currentUser.require(), employeeId,
                request));
    }
}
