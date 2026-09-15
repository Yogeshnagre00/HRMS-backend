package com.example.HRMS.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-employee payroll result for a run (table {@code payroll_employee_result};
 * Data Model 10.2, pre-statutory contract 10.2.1).
 *
 * <p>At the non-statutory (pre-statutory) stage the statutory and net columns
 * ({@code pfEmployee}, {@code pfEmployer}, {@code pt}, {@code tds},
 * {@code otherDeductions}, {@code netPay}) are LEFT NULL — NULL means "not yet
 * calculated" and is never a fabricated {@code 0.00}. {@code grossEarnings},
 * the day-basis fields, explanation and status are populated. Unique per
 * (payroll_run, employee); recalculation replaces the set atomically. Maps the
 * V18 table.
 */
@Entity
@Table(name = "payroll_employee_result")
public class PayrollEmployeeResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payroll_run_id", nullable = false)
    private UUID payrollRunId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "gross_earnings", nullable = false)
    private BigDecimal grossEarnings;

    @Column(name = "pf_employee")
    private BigDecimal pfEmployee;

    @Column(name = "pf_employer")
    private BigDecimal pfEmployer;

    @Column(name = "pt")
    private BigDecimal pt;

    @Column(name = "tds")
    private BigDecimal tds;

    @Column(name = "other_deductions")
    private BigDecimal otherDeductions;

    @Column(name = "net_pay")
    private BigDecimal netPay;

    @Column(name = "eligible_calendar_days", nullable = false)
    private BigDecimal eligibleCalendarDays;

    @Column(name = "lop_days", nullable = false)
    private BigDecimal lopDays;

    @Column(name = "payable_calendar_days", nullable = false)
    private BigDecimal payableCalendarDays;

    @Column(name = "manual_tds", nullable = false)
    private boolean manualTds;

    @Column(name = "calculation_explanation", nullable = false)
    private String calculationExplanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private PayrollResultStatus resultStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PayrollEmployeeResult() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPayrollRunId() {
        return payrollRunId;
    }

    public void setPayrollRunId(UUID payrollRunId) {
        this.payrollRunId = payrollRunId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public BigDecimal getGrossEarnings() {
        return grossEarnings;
    }

    public void setGrossEarnings(BigDecimal grossEarnings) {
        this.grossEarnings = grossEarnings;
    }

    public BigDecimal getPfEmployee() {
        return pfEmployee;
    }

    public void setPfEmployee(BigDecimal pfEmployee) {
        this.pfEmployee = pfEmployee;
    }

    public BigDecimal getPfEmployer() {
        return pfEmployer;
    }

    public void setPfEmployer(BigDecimal pfEmployer) {
        this.pfEmployer = pfEmployer;
    }

    public BigDecimal getPt() {
        return pt;
    }

    public void setPt(BigDecimal pt) {
        this.pt = pt;
    }

    public BigDecimal getTds() {
        return tds;
    }

    public void setTds(BigDecimal tds) {
        this.tds = tds;
    }

    public BigDecimal getOtherDeductions() {
        return otherDeductions;
    }

    public void setOtherDeductions(BigDecimal otherDeductions) {
        this.otherDeductions = otherDeductions;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }

    public BigDecimal getEligibleCalendarDays() {
        return eligibleCalendarDays;
    }

    public void setEligibleCalendarDays(BigDecimal eligibleCalendarDays) {
        this.eligibleCalendarDays = eligibleCalendarDays;
    }

    public BigDecimal getLopDays() {
        return lopDays;
    }

    public void setLopDays(BigDecimal lopDays) {
        this.lopDays = lopDays;
    }

    public BigDecimal getPayableCalendarDays() {
        return payableCalendarDays;
    }

    public void setPayableCalendarDays(BigDecimal payableCalendarDays) {
        this.payableCalendarDays = payableCalendarDays;
    }

    public boolean isManualTds() {
        return manualTds;
    }

    public void setManualTds(boolean manualTds) {
        this.manualTds = manualTds;
    }

    public String getCalculationExplanation() {
        return calculationExplanation;
    }

    public void setCalculationExplanation(String calculationExplanation) {
        this.calculationExplanation = calculationExplanation;
    }

    public PayrollResultStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(PayrollResultStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
