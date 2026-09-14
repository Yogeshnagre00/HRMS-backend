package com.example.HRMS.company.entity;

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
 * Single Indian payroll/legal entity belonging to a Company (table
 * {@code legal_entity}; Data Model 4.2).
 *
 * <p>Owns legal-entity identity only. Statutory applicability/configuration is
 * owned separately by {@code statutory_configuration}. v0 enforces one active
 * legal entity per company at the service layer. Maps the existing V0-002 table
 * — no schema change. Reuses {@link CompanyStatus} for its ACTIVE/INACTIVE state.
 */
@Entity
@Table(name = "legal_entity")
public class LegalEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "pan", nullable = false)
    private String pan;

    @Column(name = "financial_year_start", nullable = false)
    private LocalDate financialYearStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LegalEntity() {
        // JPA + programmatic construction
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public LocalDate getFinancialYearStart() {
        return financialYearStart;
    }

    public void setFinancialYearStart(LocalDate financialYearStart) {
        this.financialYearStart = financialYearStart;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
