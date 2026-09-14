package com.example.HRMS.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Employee API (API spec §10). Persistence
 * entities are never exposed. Enum fields are accepted as strings and matched
 * with a pattern so an invalid value yields a deterministic 400 VALIDATION_ERROR.
 * Cross-field rules (employment period) and uniqueness are enforced in the
 * service layer.
 */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /**
     * Create an employee. {@code employeeId} is the caller-supplied business
     * identifier (unique within the legal entity). The owning legal entity is
     * resolved server-side from the caller's active company scope and is never
     * accepted from the client.
     */
    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 50) String employeeId,
            @NotBlank @Size(max = 255) String fullName,
            @NotNull LocalDate joiningDate,
            LocalDate exitDate,
            @NotBlank @Size(max = 50) String employmentType,
            @Size(max = 255) String department,
            @Size(max = 255) String designation,
            @Size(max = 255) String location,
            @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]",
                    message = "pan must be a valid 10-character PAN") String pan,
            @Size(max = 20) String uan,
            @Size(max = 100) String ptState,
            @NotNull @Pattern(regexp = "NEW_REGIME|OLD_REGIME",
                    message = "taxRegime must be NEW_REGIME or OLD_REGIME") String taxRegime,
            @NotNull @Pattern(regexp = "ACTIVE|EXITED|INACTIVE",
                    message = "status must be ACTIVE, EXITED or INACTIVE") String status) {
    }

    /**
     * Update employee master. The business {@code employeeId} identity and the
     * owning legal entity are immutable in v0 and are intentionally absent here
     * (ownership/identity are not silently changed).
     */
    public record UpdateEmployeeRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotNull LocalDate joiningDate,
            LocalDate exitDate,
            @NotBlank @Size(max = 50) String employmentType,
            @Size(max = 255) String department,
            @Size(max = 255) String designation,
            @Size(max = 255) String location,
            @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]",
                    message = "pan must be a valid 10-character PAN") String pan,
            @Size(max = 20) String uan,
            @Size(max = 100) String ptState,
            @NotNull @Pattern(regexp = "NEW_REGIME|OLD_REGIME",
                    message = "taxRegime must be NEW_REGIME or OLD_REGIME") String taxRegime,
            @NotNull @Pattern(regexp = "ACTIVE|EXITED|INACTIVE",
                    message = "status must be ACTIVE, EXITED or INACTIVE") String status) {
    }

    public record EmployeeResponse(
            UUID id,
            UUID legalEntityId,
            String employeeId,
            String fullName,
            LocalDate joiningDate,
            LocalDate exitDate,
            String employmentType,
            String department,
            String designation,
            String location,
            String pan,
            String uan,
            String ptState,
            String taxRegime,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
