package com.example.HRMS.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTOs for the Non-Statutory Payroll Calculation API (API spec §16/§17;
 * task V2-008A). Persistence entities are never exposed, and no employee bank
 * account details are surfaced here.
 *
 * <p>Statutory and net-pay fields are intentionally {@code null} at the
 * pre-statutory stage — {@code null} means "not yet calculated" and is never a
 * fabricated {@code 0.00} (Data Model 10.2.1). There are no request bodies:
 * calculate/recalculate act on the path {@code runId} and the server-resolved
 * legal entity only.
 */
public final class PayrollCalculationDtos {

    private PayrollCalculationDtos() {
    }

    /**
     * Result of a calculate/recalculate operation: the run's post-calculation
     * lifecycle state and the number of employee results persisted.
     */
    public record CalculationSummaryResponse(
            UUID runId,
            String status,
            String calculationVersion,
            LocalDateTime calculatedAt,
            int employeeResultCount) {
    }

    /**
     * Summary row of an employee's payroll result (list projection). Statutory
     * and {@code netPay} fields are {@code null} pre-statutory.
     */
    public record PayrollEmployeeResultSummary(
            UUID id,
            UUID employeeId,
            String employeeBusinessId,
            String employeeName,
            BigDecimal grossEarnings,
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal pt,
            BigDecimal tds,
            BigDecimal otherDeductions,
            BigDecimal netPay,
            BigDecimal eligibleCalendarDays,
            BigDecimal lopDays,
            BigDecimal payableCalendarDays,
            String resultStatus) {
    }

    /**
     * Full explainable result for one employee: header, earning lines and
     * day-level breakdown. Statutory/net fields are {@code null} pre-statutory.
     */
    public record PayrollEmployeeResultDetail(
            UUID id,
            UUID payrollRunId,
            UUID employeeId,
            String employeeBusinessId,
            String employeeName,
            BigDecimal grossEarnings,
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal pt,
            BigDecimal tds,
            BigDecimal otherDeductions,
            BigDecimal netPay,
            BigDecimal eligibleCalendarDays,
            BigDecimal lopDays,
            BigDecimal payableCalendarDays,
            boolean manualTds,
            String calculationExplanation,
            String resultStatus,
            List<ResultLineResponse> lines,
            List<DayResultResponse> days) {
    }

    /** A single explainable result line (V2-008A emits EARNING lines only). */
    public record ResultLineResponse(
            UUID id,
            String lineType,
            String componentCode,
            String componentName,
            BigDecimal amount,
            String calculationBasis,
            String sourceRecordType,
            UUID sourceRecordId) {
    }

    /** Day-level determination snapshot for an employee result. */
    public record DayResultResponse(
            UUID id,
            LocalDate workDate,
            boolean employmentEligible,
            boolean scheduledWorkingDay,
            String leaveTreatment,
            BigDecimal leaveQuantity,
            String attendanceException,
            BigDecimal lopQuantity,
            UUID compensationRecordId,
            BigDecimal payableDayQuantity) {
    }
}
