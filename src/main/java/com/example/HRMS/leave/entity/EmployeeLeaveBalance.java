package com.example.HRMS.leave.entity;

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
 * Employee opening/current paid-leave balance (table {@code employee_leave_balance};
 * Data Model 8.2).
 *
 * <p>Belongs to an {@code Employee}, unique by Employee + Financial Year. Holds
 * the onboarding/current-FY paid-leave balance. {@code availableBalance} is
 * derived and stored as {@code openingBalance + approvedAdditions - usedQuantity}
 * (Business Rules 24.4); an insufficient/negative available balance is a
 * surfaced state, never capped or converted here. {@code financialYear} is the
 * canonical {@code YYYY-YY} key (Data Model 17.1), server-derived. Day
 * quantities use {@code NUMERIC(8,2)}. Maps the V2-004 (V10) table.
 */
@Entity
@Table(name = "employee_leave_balance")
public class EmployeeLeaveBalance {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_treatment", nullable = false)
    private LeaveTreatment leaveTreatment;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "approved_additions", nullable = false)
    private BigDecimal approvedAdditions;

    @Column(name = "used_quantity", nullable = false)
    private BigDecimal usedQuantity;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public EmployeeLeaveBalance() {
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

    public LeaveTreatment getLeaveTreatment() {
        return leaveTreatment;
    }

    public void setLeaveTreatment(LeaveTreatment leaveTreatment) {
        this.leaveTreatment = leaveTreatment;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getApprovedAdditions() {
        return approvedAdditions;
    }

    public void setApprovedAdditions(BigDecimal approvedAdditions) {
        this.approvedAdditions = approvedAdditions;
    }

    public BigDecimal getUsedQuantity() {
        return usedQuantity;
    }

    public void setUsedQuantity(BigDecimal usedQuantity) {
        this.usedQuantity = usedQuantity;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
