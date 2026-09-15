package com.example.HRMS.leave.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Employee Leave Balance API (API spec 13).
 * Persistence entities are never exposed.
 *
 * <p>This is a current-FY, paid-leave resource. PUT is a full-representation
 * set/upsert: all three input quantities are required (Data Model 8.2 marks them
 * required), and {@code availableBalance} is derived by the service
 * ({@code opening + additions - used}) — it is not client-supplied.
 * {@code financialYear} is server-derived (Data Model 17.1) and
 * {@code leaveTreatment} is server-set to {@code PAID_LEAVE}; neither is accepted
 * from the client. Quantities are day values with {@code decimal(8,2)} precision.
 */
public final class LeaveBalanceDtos {

    private LeaveBalanceDtos() {
    }

    public record SetLeaveBalanceRequest(
            @NotNull @DecimalMin(value = "0.00", message = "openingBalance must be non-negative")
            @Digits(integer = 6, fraction = 2,
                    message = "openingBalance must have at most 2 decimal places")
            BigDecimal openingBalance,

            @NotNull @DecimalMin(value = "0.00", message = "approvedAdditions must be non-negative")
            @Digits(integer = 6, fraction = 2,
                    message = "approvedAdditions must have at most 2 decimal places")
            BigDecimal approvedAdditions,

            @NotNull @DecimalMin(value = "0.00", message = "usedQuantity must be non-negative")
            @Digits(integer = 6, fraction = 2,
                    message = "usedQuantity must have at most 2 decimal places")
            BigDecimal usedQuantity) {
    }

    public record LeaveBalanceResponse(
            UUID id,
            UUID employeeId,
            String leaveTreatment,
            String financialYear,
            BigDecimal openingBalance,
            BigDecimal approvedAdditions,
            BigDecimal usedQuantity,
            BigDecimal availableBalance,
            LocalDateTime updatedAt) {
    }
}
