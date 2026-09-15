package com.example.HRMS.compensation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Effective-dated monthly compensation state for an employee (table
 * {@code compensation_record}; Data Model 9.1).
 *
 * <p>Belongs to an {@code Employee} (1 → many, non-overlapping). Represents an
 * immutable compensation state: revisions create new records and close the prior
 * open-ended record rather than mutating it. All monetary components are monthly
 * amounts. {@code effectiveTo} is server-managed ({@code null} = current). Scope
 * is derived through the employee; there is no direct legal-entity id. Maps the
 * V2-006A (V12) table.
 */
@Entity
@Table(name = "compensation_record")
public class CompensationRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "ctc_monthly", nullable = false)
    private BigDecimal ctcMonthly;

    @Column(name = "basic_monthly", nullable = false)
    private BigDecimal basicMonthly;

    @Column(name = "hra_monthly", nullable = false)
    private BigDecimal hraMonthly;

    @Column(name = "other_fixed_allowances_monthly", nullable = false)
    private BigDecimal otherFixedAllowancesMonthly;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private CompensationSource source;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CompensationRecord() {
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

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public BigDecimal getCtcMonthly() {
        return ctcMonthly;
    }

    public void setCtcMonthly(BigDecimal ctcMonthly) {
        this.ctcMonthly = ctcMonthly;
    }

    public BigDecimal getBasicMonthly() {
        return basicMonthly;
    }

    public void setBasicMonthly(BigDecimal basicMonthly) {
        this.basicMonthly = basicMonthly;
    }

    public BigDecimal getHraMonthly() {
        return hraMonthly;
    }

    public void setHraMonthly(BigDecimal hraMonthly) {
        this.hraMonthly = hraMonthly;
    }

    public BigDecimal getOtherFixedAllowancesMonthly() {
        return otherFixedAllowancesMonthly;
    }

    public void setOtherFixedAllowancesMonthly(BigDecimal otherFixedAllowancesMonthly) {
        this.otherFixedAllowancesMonthly = otherFixedAllowancesMonthly;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public CompensationSource getSource() {
        return source;
    }

    public void setSource(CompensationSource source) {
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
