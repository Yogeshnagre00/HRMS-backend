package com.example.HRMS.workcalendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated assignment of a {@link WorkCalendar} to an employee (table
 * {@code work_calendar_assignment}; Data Model 6.2).
 *
 * <p>Effective-date ranges must not overlap for an employee (enforced in the
 * service layer); a later payroll calculation resolves the applicable
 * assignment per payroll date. The assigned calendar must belong to the
 * employee's legal entity (cross-entity assignment is rejected). Maps the
 * existing V0-002 (V2) table; the {@code employee_id} foreign key is finalized
 * in V14.
 */
@Entity
@Table(name = "work_calendar_assignment")
public class WorkCalendarAssignment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_calendar_id", nullable = false)
    private UUID workCalendarId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public WorkCalendarAssignment() {
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

    public UUID getWorkCalendarId() {
        return workCalendarId;
    }

    public void setWorkCalendarId(UUID workCalendarId) {
        this.workCalendarId = workCalendarId;
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

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
