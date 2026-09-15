package com.example.HRMS.payrollinput.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Independent payroll-period arrear for one employee and payroll run (table
 * {@code arrear}; Data Model 9.3).
 *
 * <p>An arrear is a separate earning input; it never overwrites or modifies the
 * employee's {@code CompensationRecord}/base salary and is not automatically
 * prorated. It becomes an earning line during a later payroll calculation.
 * Creation is permitted only while the owning {@code PayrollRun} is in
 * {@code DRAFT} (V2-008A.6.1); the amount is non-negative and
 * {@code period_reference}/{@code reason} are required. Multiple arrears may
 * exist for the same employee and payroll run. Maps the V17 table.
 */
@Entity
@Table(name = "arrear")
public class Arrear {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "payroll_run_id", nullable = false)
    private UUID payrollRunId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "period_reference", nullable = false)
    private String periodReference;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Arrear() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public UUID getPayrollRunId() {
        return payrollRunId;
    }

    public void setPayrollRunId(UUID payrollRunId) {
        this.payrollRunId = payrollRunId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPeriodReference() {
        return periodReference;
    }

    public void setPeriodReference(String periodReference) {
        this.periodReference = periodReference;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
