package com.example.HRMS.payrollinput.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Independent payroll-period variable earning for one employee and payroll run
 * (table {@code variable_earning}; Data Model 9.2).
 *
 * <p>Not part of the recurring {@code CompensationRecord}. It becomes an EARNING
 * result line during a later payroll calculation and is never automatically
 * prorated. Creation is permitted only while the owning {@code PayrollRun} is in
 * {@code DRAFT} (V2-008A.6.1); the amount is non-negative. Multiple variable
 * earnings may exist for the same employee and payroll run. Maps the V17 table.
 */
@Entity
@Table(name = "variable_earning")
public class VariableEarning {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "payroll_run_id", nullable = false)
    private UUID payrollRunId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "source")
    private String source;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public VariableEarning() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
