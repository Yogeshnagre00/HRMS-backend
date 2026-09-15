package com.example.HRMS.attendance.entity;

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
 * Admin-entered attendance exception for one employee and date (table
 * {@code attendance_exception}; Data Model 8.3).
 *
 * <p>v0 attendance is exception-based: a scheduled working day is Present unless
 * an exception exists, so only exceptions are stored. An exception is valid only
 * within the employee's employment period, and at most one exception may exist
 * per employee per date (enforced in the service). This is a payroll source
 * input only — it carries no monetary or payroll-result data. Maps the V15
 * table.
 */
@Entity
@Table(name = "attendance_exception")
public class AttendanceException {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false)
    private AttendanceExceptionType exceptionType;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AttendanceException() {
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

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public AttendanceExceptionType getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(AttendanceExceptionType exceptionType) {
        this.exceptionType = exceptionType;
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
