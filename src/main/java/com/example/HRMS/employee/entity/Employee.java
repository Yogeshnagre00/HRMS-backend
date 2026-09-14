package com.example.HRMS.employee.entity;

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
 * Employee master record (table {@code employee}; Data Model 7.1).
 *
 * <p>Belongs to a {@code LegalEntity}. Carries two identifiers: the surrogate
 * {@link #id} (internal UUID PK) and {@link #employeeId} (the business Employee
 * ID, unique within the legal entity). This entity owns employee identity and
 * organizational/statutory-identity fields only — bank details, opening tax
 * state, opening leave and calendar assignment are separate later slices and
 * are deliberately not modelled here. Maps the V2-001 (V7) table.
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "employment_type", nullable = false)
    private String employmentType;

    @Column(name = "department")
    private String department;

    @Column(name = "designation")
    private String designation;

    @Column(name = "location")
    private String location;

    @Column(name = "pan", nullable = false)
    private String pan;

    @Column(name = "uan")
    private String uan;

    @Column(name = "pt_state")
    private String ptState;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false)
    private TaxRegime taxRegime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmployeeStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Employee() {
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

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getUan() {
        return uan;
    }

    public void setUan(String uan) {
        this.uan = uan;
    }

    public String getPtState() {
        return ptState;
    }

    public void setPtState(String ptState) {
        this.ptState = ptState;
    }

    public TaxRegime getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(TaxRegime taxRegime) {
        this.taxRegime = taxRegime;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
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
