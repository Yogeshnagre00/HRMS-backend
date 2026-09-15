package com.example.HRMS.workcalendar.entity;

import com.example.HRMS.company.entity.CompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated work calendar for a legal entity (table {@code work_calendar};
 * Data Model 6.1).
 *
 * <p>v0 supports exactly one standard 5-day pattern: Monday–Friday scheduled,
 * Saturday–Sunday weekly off ({@code mondayToFriday = true},
 * {@code saturdaySundayWeeklyOff = true}). Employees reference a calendar through
 * {@link WorkCalendarAssignment} rather than embedding a hardcoded schedule.
 * Effective-date ranges must not overlap for a legal entity (enforced in the
 * service layer). Reuses {@link CompanyStatus} (ACTIVE/INACTIVE). Maps the
 * existing V0-002 (V2) table — no schema change here.
 */
@Entity
@Table(name = "work_calendar")
public class WorkCalendar {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "monday_to_friday", nullable = false)
    private boolean mondayToFriday;

    @Column(name = "saturday_sunday_weekly_off", nullable = false)
    private boolean saturdaySundayWeeklyOff;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyStatus status;

    public WorkCalendar() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isMondayToFriday() {
        return mondayToFriday;
    }

    public void setMondayToFriday(boolean mondayToFriday) {
        this.mondayToFriday = mondayToFriday;
    }

    public boolean isSaturdaySundayWeeklyOff() {
        return saturdaySundayWeeklyOff;
    }

    public void setSaturdaySundayWeeklyOff(boolean saturdaySundayWeeklyOff) {
        this.saturdaySundayWeeklyOff = saturdaySundayWeeklyOff;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }
}
