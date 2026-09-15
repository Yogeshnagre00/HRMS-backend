package com.example.HRMS.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Attendance Exception API (API spec 12).
 * Persistence entities are never exposed.
 *
 * <p>The exception is scoped to an employee (resolved server-side within the
 * caller's company). {@code exceptionType} is one of FULL_DAY_ABSENCE / HALF_DAY
 * / LOP; {@code quantity} is a day value (0.5 or 1.0) whose relationship to the
 * type is enforced in the service. {@code createdBy}/{@code createdAt} are
 * server-controlled and are not accepted from the client.
 */
public final class AttendanceExceptionDtos {

    private AttendanceExceptionDtos() {
    }

    /**
     * Create/update an attendance exception. {@code employeeId} is supplied on
     * create (path is a flat collection); it is immutable on update.
     */
    public record UpsertAttendanceExceptionRequest(
            @NotNull UUID employeeId,
            @NotNull LocalDate attendanceDate,
            @NotNull @Pattern(regexp = "FULL_DAY_ABSENCE|HALF_DAY|LOP",
                    message = "exceptionType must be FULL_DAY_ABSENCE, HALF_DAY or LOP")
            String exceptionType,
            @NotNull BigDecimal quantity,
            @Size(max = 255) String reason) {
    }

    public record AttendanceExceptionResponse(
            UUID id,
            UUID employeeId,
            LocalDate attendanceDate,
            String exceptionType,
            BigDecimal quantity,
            String reason,
            UUID createdBy,
            LocalDateTime createdAt) {
    }
}
