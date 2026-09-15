package com.example.HRMS.payrollinput.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Variable Earning and Arrear APIs (API spec
 * 14.7). Persistence entities are never exposed.
 *
 * <p>Both are payroll-period inputs attached to a {@code PayrollRun}. The
 * employee is taken from the path; the client supplies {@code payrollRunId} and
 * the input fields. {@code amount} is a non-negative {@code decimal(18,2)}
 * (negative rejected). Creation is permitted only while the run is in DRAFT
 * (V2-008A.6.1). {@code createdBy}/{@code createdAt} are server-controlled.
 */
public final class PayrollInputDtos {

    private PayrollInputDtos() {
    }

    /** Create a variable earning for the path employee against a payroll run. */
    public record CreateVariableEarningRequest(
            @NotNull UUID payrollRunId,
            @NotBlank @Size(max = 255) String description,
            @NotNull BigDecimal amount,
            @Size(max = 255) String source) {
    }

    public record VariableEarningResponse(
            UUID id,
            UUID employeeId,
            UUID payrollRunId,
            String description,
            BigDecimal amount,
            String source,
            UUID createdBy,
            LocalDateTime createdAt) {
    }

    /** Create an arrear for the path employee against a payroll run. */
    public record CreateArrearRequest(
            @NotNull UUID payrollRunId,
            @NotNull BigDecimal amount,
            @NotBlank @Size(max = 255) String periodReference,
            @NotBlank @Size(max = 255) String reason) {
    }

    public record ArrearResponse(
            UUID id,
            UUID employeeId,
            UUID payrollRunId,
            BigDecimal amount,
            String periodReference,
            String reason,
            UUID createdBy,
            LocalDateTime createdAt) {
    }
}
