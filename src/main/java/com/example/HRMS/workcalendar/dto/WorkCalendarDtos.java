package com.example.HRMS.workcalendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request/response DTOs for the Work Calendar and Employee Work Calendar
 * Assignment APIs (API spec 9). Persistence entities are never exposed.
 *
 * <p>The owning legal entity is resolved server-side and is never accepted from
 * the client. v0 supports exactly one standard 5-day pattern
 * ({@code mondayToFriday = true}, {@code saturdaySundayWeeklyOff = true});
 * unsupported configurations are rejected (400), never silently normalized.
 */
public final class WorkCalendarDtos {

    private WorkCalendarDtos() {
    }

    /**
     * Create a work calendar. {@code effectiveTo} is optional (null = open-ended);
     * when supplied it must be on/after {@code effectiveFrom}. {@code status} is
     * ACTIVE/INACTIVE. The v0 pattern flags must both be true.
     */
    public record CreateWorkCalendarRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull Boolean mondayToFriday,
            @NotNull Boolean saturdaySundayWeeklyOff,
            @NotNull @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|INACTIVE",
                    message = "status must be ACTIVE or INACTIVE") String status) {
    }

    /**
     * Update a work calendar (full representation). Same fields as create; the
     * owning legal entity is immutable and absent here.
     */
    public record UpdateWorkCalendarRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull Boolean mondayToFriday,
            @NotNull Boolean saturdaySundayWeeklyOff,
            @NotNull @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|INACTIVE",
                    message = "status must be ACTIVE or INACTIVE") String status) {
    }

    public record WorkCalendarResponse(
            UUID id,
            UUID legalEntityId,
            String name,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean mondayToFriday,
            boolean saturdaySundayWeeklyOff,
            String status) {
    }

    /**
     * Assign a work calendar to an employee (PUT). The employee is taken from the
     * path; the calendar and effective dates come from the body. The calendar
     * must belong to the employee's legal entity.
     */
    public record AssignWorkCalendarRequest(
            @NotNull UUID workCalendarId,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public record WorkCalendarAssignmentResponse(
            UUID id,
            UUID employeeId,
            UUID workCalendarId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            UUID createdBy) {
    }
}
