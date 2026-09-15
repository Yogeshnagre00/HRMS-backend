package com.example.HRMS.payroll.entity;

import com.example.HRMS.attendance.entity.AttendanceExceptionType;
import com.example.HRMS.leave.entity.LeaveEntryTreatment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Day-level snapshot of a {@link PayrollEmployeeResult} (table
 * {@code payroll_day_result}; Data Model 10.4).
 *
 * <p>One row per relevant payroll-month calendar date, preserving the inputs and
 * outcome used to determine that day: employment eligibility, scheduled-day,
 * leave, attendance exception, resulting LOP quantity, the compensation record
 * effective that day and the payable-day quantity. This is the reproducibility
 * record for the day-level payroll determination. Maps the V18 table.
 */
@Entity
@Table(name = "payroll_day_result")
public class PayrollDayResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payroll_employee_result_id", nullable = false)
    private UUID payrollEmployeeResultId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "employment_eligible", nullable = false)
    private boolean employmentEligible;

    @Column(name = "scheduled_working_day", nullable = false)
    private boolean scheduledWorkingDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_treatment")
    private LeaveEntryTreatment leaveTreatment;

    @Column(name = "leave_quantity")
    private BigDecimal leaveQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_exception")
    private AttendanceExceptionType attendanceException;

    @Column(name = "lop_quantity", nullable = false)
    private BigDecimal lopQuantity;

    @Column(name = "compensation_record_id")
    private UUID compensationRecordId;

    @Column(name = "payable_day_quantity", nullable = false)
    private BigDecimal payableDayQuantity;

    public PayrollDayResult() {
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

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public boolean isEmploymentEligible() {
        return employmentEligible;
    }

    public void setEmploymentEligible(boolean employmentEligible) {
        this.employmentEligible = employmentEligible;
    }

    public boolean isScheduledWorkingDay() {
        return scheduledWorkingDay;
    }

    public void setScheduledWorkingDay(boolean scheduledWorkingDay) {
        this.scheduledWorkingDay = scheduledWorkingDay;
    }

    public LeaveEntryTreatment getLeaveTreatment() {
        return leaveTreatment;
    }

    public void setLeaveTreatment(LeaveEntryTreatment leaveTreatment) {
        this.leaveTreatment = leaveTreatment;
    }

    public BigDecimal getLeaveQuantity() {
        return leaveQuantity;
    }

    public void setLeaveQuantity(BigDecimal leaveQuantity) {
        this.leaveQuantity = leaveQuantity;
    }

    public AttendanceExceptionType getAttendanceException() {
        return attendanceException;
    }

    public void setAttendanceException(AttendanceExceptionType attendanceException) {
        this.attendanceException = attendanceException;
    }

    public BigDecimal getLopQuantity() {
        return lopQuantity;
    }

    public void setLopQuantity(BigDecimal lopQuantity) {
        this.lopQuantity = lopQuantity;
    }

    public UUID getCompensationRecordId() {
        return compensationRecordId;
    }

    public void setCompensationRecordId(UUID compensationRecordId) {
        this.compensationRecordId = compensationRecordId;
    }

    public BigDecimal getPayableDayQuantity() {
        return payableDayQuantity;
    }

    public void setPayableDayQuantity(BigDecimal payableDayQuantity) {
        this.payableDayQuantity = payableDayQuantity;
    }
}
