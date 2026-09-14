package com.example.HRMS.tax.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Employee Opening Tax State API (API spec 10.1).
 * Persistence entities are never exposed.
 *
 * <p>This is a current-FY resource: {@code financialYear} is server-derived
 * (Data Model 17.1) and is NOT accepted from the client. {@code source} is
 * server-assigned {@code MANUAL} and is NOT accepted from the client. PATCH is a
 * partial update — either monetary field may be supplied; on create both are
 * required (enforced in the service).
 */
public final class OpeningTaxStateDtos {

    private OpeningTaxStateDtos() {
    }

    public record PatchOpeningTaxStateRequest(
            @DecimalMin(value = "0.00", message = "cumulativeTaxableIncome must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "cumulativeTaxableIncome must have at most 2 decimal places")
            BigDecimal cumulativeTaxableIncome,

            @DecimalMin(value = "0.00", message = "tdsAlreadyDeducted must be non-negative")
            @Digits(integer = 16, fraction = 2,
                    message = "tdsAlreadyDeducted must have at most 2 decimal places")
            BigDecimal tdsAlreadyDeducted) {
    }

    public record OpeningTaxStateResponse(
            UUID id,
            UUID employeeId,
            String financialYear,
            BigDecimal cumulativeTaxableIncome,
            BigDecimal tdsAlreadyDeducted,
            String source,
            LocalDateTime createdAt) {
    }
}
