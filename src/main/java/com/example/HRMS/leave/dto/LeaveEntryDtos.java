package com.example.HRMS.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Leave Entry API (API spec 13). Persistence
 * entities are never exposed.
 *
 * <p>A leave entry is scoped to an employee (resolved server-side within the
 * caller's company). {@code treatment} is PAID_LEAVE or UNPAID_LOP_LEAVE;
 * {@code quantity} is a day value (0.5 or 1.0) enforced in the service.
 * {@code status}, {@code createdBy} and {@code createdAt} are server-controlled
 * and are not accepted from the client on create. FY-derived balance mutation
 * for PAID_LEAVE is owned by this layer (V2-008A.2).
 */
public final class LeaveEntryDtos {

    private LeaveEntryDtos() {
    }

    /** Create a leave entry. The entry is created in {@code RECORDED} status. */
    public record CreateLeaveEntryRequest(
            @NotNull UUID employeeId,
            @NotNull LocalDate leaveDate,
            @NotNull @Pattern(regexp = "PAID_LEAVE|UNPAID_LOP_LEAVE",
                    message = "treatment must be PAID_LEAVE or UNPAID_LOP_LEAVE")
            String treatment,
            @NotNull BigDecimal quantity,
            @Size(max = 255) String reason) {
    }

    /**
     * Update a leave entry. In v0 the only authoritative transition is
     * cancellation ({@code status: CANCELLED}); the entry's identity fields
     * (employee, date, treatment, quantity) are not mutable via this contract.
     */
    public record UpdateLeaveEntryRequest(
            @NotNull @Pattern(regexp = "CANCELLED",
                    message = "the only supported status transition is CANCELLED")
            String status) {
    }

    public record LeaveEntryResponse(
            UUID id,
            UUID employeeId,
            LocalDate leaveDate,
            String treatment,
            BigDecimal quantity,
            String reason,
            String status,
            UUID createdBy,
            LocalDateTime createdAt) {
    }
}
