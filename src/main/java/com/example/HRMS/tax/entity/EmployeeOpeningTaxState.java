package com.example.HRMS.tax.entity;

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
 * Employee opening financial-year tax state (table
 * {@code employee_opening_tax_state}; Data Model 7.2).
 *
 * <p>Belongs to an {@code Employee}. Stores the current-FY opening values
 * (cumulative taxable income and TDS already deducted) that later automatic
 * New-Regime TDS depends on. Unique by Employee + Financial Year. The
 * {@code financialYear} is the canonical {@code YYYY-YY} key (Data Model 17.1),
 * server-derived and never client-supplied. This entity stores state only; it
 * performs no tax/TDS calculation. Maps the V2-003 (V9) table.
 */
@Entity
@Table(name = "employee_opening_tax_state")
public class EmployeeOpeningTaxState {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(name = "cumulative_taxable_income", nullable = false)
    private BigDecimal cumulativeTaxableIncome;

    @Column(name = "tds_already_deducted", nullable = false)
    private BigDecimal tdsAlreadyDeducted;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private OpeningTaxStateSource source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public EmployeeOpeningTaxState() {
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

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public BigDecimal getCumulativeTaxableIncome() {
        return cumulativeTaxableIncome;
    }

    public void setCumulativeTaxableIncome(BigDecimal cumulativeTaxableIncome) {
        this.cumulativeTaxableIncome = cumulativeTaxableIncome;
    }

    public BigDecimal getTdsAlreadyDeducted() {
        return tdsAlreadyDeducted;
    }

    public void setTdsAlreadyDeducted(BigDecimal tdsAlreadyDeducted) {
        this.tdsAlreadyDeducted = tdsAlreadyDeducted;
    }

    public OpeningTaxStateSource getSource() {
        return source;
    }

    public void setSource(OpeningTaxStateSource source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
