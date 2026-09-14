package com.example.HRMS.bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Employee bank account (table {@code employee_bank_account}; Data Model 7.3).
 *
 * <p>Belongs to an {@code Employee}. Effective-dated with a designated primary;
 * v0 manages the single current (open) account per employee via the singular
 * bank-account API. Bank details feed the later generic bank-transfer CSV;
 * payment execution/integration is out of v0.
 *
 * <p>{@code accountNumber} is sensitive: it is never logged in full and is not
 * placed into audit metadata (the audit references this record's id). The
 * authoritative v0.1 documents do not specify an encryption-at-rest/masking
 * mechanism, so none is invented here (reported as an open decision). Maps the
 * V2-002 (V8) table.
 */
@Entity
@Table(name = "employee_bank_account")
public class EmployeeBankAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "ifsc", nullable = false)
    private String ifsc;

    @Column(name = "account_holder_name")
    private String accountHolderName;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BankAccountStatus status;

    public EmployeeBankAccount() {
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

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
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

    public BankAccountStatus getStatus() {
        return status;
    }

    public void setStatus(BankAccountStatus status) {
        this.status = status;
    }
}
