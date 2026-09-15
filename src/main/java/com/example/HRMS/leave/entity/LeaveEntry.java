package com.example.HRMS.leave.entity;

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
 * Admin-entered, date-specific leave record for one employee and date (table
 * {@code leave_entry}; Data Model 8.1).
 *
 * <p>Distinct from {@link EmployeeLeaveBalance} (balance state). A
 * {@code PAID_LEAVE} entry consumes the employee's current-FY paid-leave balance
 * on creation and reverses it on cancellation; an {@code UNPAID_LOP_LEAVE} entry
 * records explicit LOP and never touches the balance. An entry is valid only
 * within the employee's employment period; at most one RECORDED entry may exist
 * per employee per date; a same-date attendance exception is a blocking
 * conflict. This is a payroll source input only — no monetary/payroll-result
 * data. Maps the V16 table.
 */
@Entity
@Table(name = "leave_entry")
public class LeaveEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment", nullable = false)
    private LeaveEntryTreatment treatment;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaveEntryStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LeaveEntry() {
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

    public LocalDate getLeaveDate() {
        return leaveDate;
    }

    public void setLeaveDate(LocalDate leaveDate) {
        this.leaveDate = leaveDate;
    }

    public LeaveEntryTreatment getTreatment() {
        return treatment;
    }

    public void setTreatment(LeaveEntryTreatment treatment) {
        this.treatment = treatment;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LeaveEntryStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveEntryStatus status) {
        this.status = status;
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
