package com.example.HRMS.compensation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Compensation API (API spec §14.1). Persistence
 * entities are never exposed.
 *
 * <p>All monetary values are monthly amounts, required and non-negative, with
 * {@code decimal(18,2)} precision. {@code effectiveTo}, {@code source},
 * {@code createdBy} and {@code createdAt} are server-controlled and are not
 * accepted from the client. No cross-component sum validation (CTC is
 * informational).
 */
public final class CompensationDtos {

    private CompensationDtos() {
    }

    /**
     * Create the first compensation record OR create a salary revision (same
     * client-supplied fields; the endpoint determines create vs revise).
     */
    public record CompensationRequest(
            @NotNull LocalDate effectiveFrom,

            @NotNull @DecimalMin(value = "0.00", message = "ctcMonthly must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "ctcMonthly must have at most 2 decimal places")
            BigDecimal ctcMonthly,

            @NotNull @DecimalMin(value = "0.00", message = "basicMonthly must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "basicMonthly must have at most 2 decimal places")
            BigDecimal basicMonthly,

            @NotNull @DecimalMin(value = "0.00", message = "hraMonthly must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "hraMonthly must have at most 2 decimal places")
            BigDecimal hraMonthly,

            @NotNull @DecimalMin(value = "0.00", message = "daMonthly must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "daMonthly must have at most 2 decimal places")
            BigDecimal daMonthly,

            @NotNull @DecimalMin(value = "0.00",
                    message = "otherFixedAllowancesMonthly must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "otherFixedAllowancesMonthly must have at most 2 decimal places")
            BigDecimal otherFixedAllowancesMonthly,

            @Size(max = 255) String reason) {
    }

    public record CompensationResponse(
            UUID id,
            UUID employeeId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal ctcMonthly,
            BigDecimal basicMonthly,
            BigDecimal hraMonthly,
            BigDecimal daMonthly,
            BigDecimal otherFixedAllowancesMonthly,
            String reason,
            String source,
            UUID createdBy,
            LocalDateTime createdAt) {
    }
}
