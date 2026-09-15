package com.example.HRMS.payroll.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Payroll Run API (API spec 15). Persistence
 * entities are never exposed.
 *
 * <p>The create request carries only client-permitted fields: the payroll month
 * (canonical first day of a calendar month) and the verified statutory
 * rule-version set to reference. The owning legal entity, financial year,
 * status, calculation version, correction lineage and all lifecycle
 * timestamps/actors are server-controlled and are never accepted from the
 * client.
 */
public final class PayrollRunDtos {

    private PayrollRunDtos() {
    }

    /**
     * Create a monthly payroll run. {@code payrollMonth} must be the first day of
     * the target calendar month (e.g. {@code 2026-09-01}); a non-first-day date
     * is rejected with 400. {@code ruleVersionSetId} must reference an existing
     * verified statutory rule-version set.
     */
    public record CreatePayrollRunRequest(
            @NotNull LocalDate payrollMonth,
            @NotNull UUID ruleVersionSetId) {
    }

    /**
     * Payroll run view. {@code eligibleEmployeeCount} is the size of the
     * automatically-determined employee population (employment period intersects
     * the payroll month); it is a Foundation convenience count and not a
     * calculation result. Lifecycle timestamps/actors are null until the
     * corresponding later-slice transition occurs.
     */
    public record PayrollRunResponse(
            UUID id,
            UUID legalEntityId,
            LocalDate payrollMonth,
            String financialYear,
            String status,
            String calculationVersion,
            UUID ruleVersionSetId,
            UUID parentPayrollRunId,
            long eligibleEmployeeCount,
            LocalDateTime calculatedAt,
            LocalDateTime approvedAt,
            UUID approvedBy,
            LocalDateTime lockedAt,
            UUID lockedBy,
            UUID createdBy,
            LocalDateTime createdAt) {
    }
}
