package com.example.HRMS.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Monthly payroll run (table {@code payroll_run}; Data Model 10.1).
 *
 * <p>The payroll lifecycle aggregate for one legal entity and payroll month. The
 * V2-007 Payroll Run Foundation persists the run, its {@code DRAFT} status,
 * legal-entity ownership, the referenced statutory rule-version set, the
 * server-derived financial year and creation metadata. It carries the lifecycle
 * metadata columns (calculated/approved/locked, correction lineage) so later
 * slices can populate them, but this slice never calculates payroll and never
 * creates employee/day/statutory result, health-check, approval, lock or output
 * records.
 *
 * <p>Ownership is {@code legal_entity_id}; company scope is derived through the
 * legal entity. A primary run is unique by (legal entity, payroll_month); that
 * conditional invariant is enforced in the service layer (correction lineage
 * uses {@code parent_payroll_run_id}). Maps the V13 table.
 */
@Entity
@Table(name = "payroll_run")
public class PayrollRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "payroll_month", nullable = false)
    private LocalDate payrollMonth;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollRunStatus status;

    @Column(name = "calculation_version", nullable = false)
    private String calculationVersion;

    @Column(name = "rule_version_set_id", nullable = false)
    private UUID ruleVersionSetId;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "parent_payroll_run_id")
    private UUID parentPayrollRunId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PayrollRun() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(UUID legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public LocalDate getPayrollMonth() {
        return payrollMonth;
    }

    public void setPayrollMonth(LocalDate payrollMonth) {
        this.payrollMonth = payrollMonth;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public PayrollRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollRunStatus status) {
        this.status = status;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public void setCalculationVersion(String calculationVersion) {
        this.calculationVersion = calculationVersion;
    }

    public UUID getRuleVersionSetId() {
        return ruleVersionSetId;
    }

    public void setRuleVersionSetId(UUID ruleVersionSetId) {
        this.ruleVersionSetId = ruleVersionSetId;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(UUID lockedBy) {
        this.lockedBy = lockedBy;
    }

    public UUID getParentPayrollRunId() {
        return parentPayrollRunId;
    }

    public void setParentPayrollRunId(UUID parentPayrollRunId) {
        this.parentPayrollRunId = parentPayrollRunId;
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
