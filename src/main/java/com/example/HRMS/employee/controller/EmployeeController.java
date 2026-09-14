package com.example.HRMS.employee.controller;

import com.example.HRMS.common.api.PageResponse;
import com.example.HRMS.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.example.HRMS.employee.dto.EmployeeDtos.EmployeeResponse;
import com.example.HRMS.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.example.HRMS.employee.service.EmployeeService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Employee API (API spec §10). Company-scoped employee master management, so it
 * reuses the {@code company.admin} permission. Thin controller; scope isolation,
 * uniqueness, validation and auditing live in {@link EmployeeService}.
 *
 * <p>The list endpoint is paginated ("pagination from day one"): {@code page}
 * (0-based), {@code size}, and a fixed deterministic default ordering by the
 * business Employee ID. The out-of-scope employee sub-resources (bank account,
 * payroll readiness, CSV import) are intentionally not implemented here.
 */
@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employee", description = "Employee master management")
public class EmployeeController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    public EmployeeController(EmployeeService employeeService, CurrentUser currentUser) {
        this.employeeService = employeeService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List employees (paginated)")
    public ResponseEntity<PageResponse<EmployeeResponse>> listEmployees(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        // Deterministic ordering: business Employee ID, then surrogate id for stable ties.
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("employeeId"), Sort.Order.asc("id")));
        return ResponseEntity.ok(PageResponse.from(
                employeeService.listEmployees(currentUser.require(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create employee",
            description = "Creates an employee in the caller's legal entity. The business "
                    + "Employee ID must be unique within the entity (409 on duplicate).")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse created = employeeService.createEmployee(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read employee by id")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(employeeService.getEmployee(currentUser.require(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Update employee master")
    public ResponseEntity<EmployeeResponse> updateEmployee(@PathVariable java.util.UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(currentUser.require(), id, request));
    }
}
