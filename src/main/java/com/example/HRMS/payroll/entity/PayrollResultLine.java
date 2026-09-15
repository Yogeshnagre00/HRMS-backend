package com.example.HRMS.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single explainable line of a {@link PayrollEmployeeResult} (table
 * {@code payroll_result_line}; Data Model 10.3).
 *
 * <p>The non-statutory calculation emits only {@code EARNING} lines (prorated
 * Basic/HRA/Other Fixed Allowances, each variable earning, each arrear).
 * {@code calculationBasis} holds a JSON explanation of the amount; optional
 * {@code sourceRecordType}/{@code sourceRecordId} reference the originating
 * input (e.g. compensation_record, variable_earning, arrear). Maps the V18
 * table.
 */
@Entity
@Table(name = "payroll_result_line")
public class PayrollResultLine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payroll_employee_result_id", nullable = false)
    private UUID payrollEmployeeResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false)
    private PayrollLineType lineType;

    @Column(name = "component_code", nullable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "calculation_basis", nullable = false)
    private String calculationBasis;

    @Column(name = "source_record_type")
    private String sourceRecordType;

    @Column(name = "source_record_id")
    private UUID sourceRecordId;

    public PayrollResultLine() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPayrollEmployeeResultId() {
        return payrollEmployeeResultId;
    }

    public void setPayrollEmployeeResultId(UUID payrollEmployeeResultId) {
        this.payrollEmployeeResultId = payrollEmployeeResultId;
    }

    public PayrollLineType getLineType() {
        return lineType;
    }

    public void setLineType(PayrollLineType lineType) {
        this.lineType = lineType;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public void setComponentCode(String componentCode) {
        this.componentCode = componentCode;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCalculationBasis() {
        return calculationBasis;
    }

    public void setCalculationBasis(String calculationBasis) {
        this.calculationBasis = calculationBasis;
    }

    public String getSourceRecordType() {
        return sourceRecordType;
    }

    public void setSourceRecordType(String sourceRecordType) {
        this.sourceRecordType = sourceRecordType;
    }

    public UUID getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(UUID sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }
}
