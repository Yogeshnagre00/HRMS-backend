package com.example.HRMS.workcalendar.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.CompanyStatus;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.AssignWorkCalendarRequest;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.CreateWorkCalendarRequest;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.UpdateWorkCalendarRequest;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.WorkCalendarAssignmentResponse;
import com.example.HRMS.workcalendar.dto.WorkCalendarDtos.WorkCalendarResponse;
import com.example.HRMS.workcalendar.entity.WorkCalendar;
import com.example.HRMS.workcalendar.entity.WorkCalendarAssignment;
import com.example.HRMS.workcalendar.repository.WorkCalendarAssignmentRepository;
import com.example.HRMS.workcalendar.repository.WorkCalendarRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work Calendar and Employee Work Calendar Assignment use cases (API spec 9;
 * task V2-008A.3).
 *
 * <p>Company/legal-entity scoped server-side (the client never supplies a
 * company/legal-entity id). v0 supports exactly one standard 5-day pattern
 * (Monday–Friday scheduled, Saturday–Sunday weekly off); unsupported
 * configurations are rejected. Calendar effective ranges must not overlap for a
 * legal entity, and assignment effective ranges must not overlap for an
 * employee — both enforced here (portable to H2; no partial index). An
 * assignment's calendar must belong to the employee's legal entity. This slice
 * performs no payroll calculation. Material writes are audited.
 */
@Service
public class WorkCalendarService {

    private final WorkCalendarRepository calendarRepository;
    private final WorkCalendarAssignmentRepository assignmentRepository;
    private final LegalEntityService legalEntityService;
    private final EmployeeService employeeService;
    private final AuditService auditService;

    public WorkCalendarService(WorkCalendarRepository calendarRepository,
                               WorkCalendarAssignmentRepository assignmentRepository,
                               LegalEntityService legalEntityService,
                               EmployeeService employeeService,
                               AuditService auditService) {
        this.calendarRepository = calendarRepository;
        this.assignmentRepository = assignmentRepository;
        this.legalEntityService = legalEntityService;
        this.employeeService = employeeService;
        this.auditService = auditService;
    }

    // ---- Work Calendar ----------------------------------------------------

    @Transactional
    public WorkCalendarResponse createCalendar(AuthenticatedUser actor,
                                               CreateWorkCalendarRequest request) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        validateEffectiveRange(request.effectiveFrom(), request.effectiveTo());
        validateV0Pattern(request.mondayToFriday(), request.saturdaySundayWeeklyOff());
        assertNoCalendarOverlap(legalEntity.getId(), request.effectiveFrom(),
                request.effectiveTo(), null);

        WorkCalendar calendar = new WorkCalendar();
        calendar.setId(UUID.randomUUID());
        calendar.setLegalEntityId(legalEntity.getId());
        calendar.setName(request.name());
        calendar.setEffectiveFrom(request.effectiveFrom());
        calendar.setEffectiveTo(request.effectiveTo());
        calendar.setMondayToFriday(request.mondayToFriday());
        calendar.setSaturdaySundayWeeklyOff(request.saturdaySundayWeeklyOff());
        calendar.setStatus(CompanyStatus.valueOf(request.status()));
        calendarRepository.save(calendar);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.WORK_CALENDAR_CREATED,
                AuditActions.ENTITY_WORK_CALENDAR, calendar.getId(), "SUCCESS", null, null));
        return toResponse(calendar);
    }

    @Transactional
    public WorkCalendarResponse updateCalendar(AuthenticatedUser actor, UUID id,
                                               UpdateWorkCalendarRequest request) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        WorkCalendar calendar = calendarRepository.findByIdAndLegalEntityId(id, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.WORK_CALENDAR_NOT_FOUND));
        validateEffectiveRange(request.effectiveFrom(), request.effectiveTo());
        validateV0Pattern(request.mondayToFriday(), request.saturdaySundayWeeklyOff());
        assertNoCalendarOverlap(legalEntity.getId(), request.effectiveFrom(),
                request.effectiveTo(), calendar.getId());

        calendar.setName(request.name());
        calendar.setEffectiveFrom(request.effectiveFrom());
        calendar.setEffectiveTo(request.effectiveTo());
        calendar.setMondayToFriday(request.mondayToFriday());
        calendar.setSaturdaySundayWeeklyOff(request.saturdaySundayWeeklyOff());
        calendar.setStatus(CompanyStatus.valueOf(request.status()));
        calendarRepository.save(calendar);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.WORK_CALENDAR_UPDATED,
                AuditActions.ENTITY_WORK_CALENDAR, calendar.getId(), "SUCCESS", null, null));
        return toResponse(calendar);
    }

    @Transactional(readOnly = true)
    public Page<WorkCalendarResponse> listCalendars(AuthenticatedUser actor, Pageable pageable) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        return calendarRepository.findByLegalEntityId(legalEntity.getId(), pageable)
                .map(WorkCalendarService::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkCalendarResponse getCalendar(AuthenticatedUser actor, UUID id) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        return calendarRepository.findByIdAndLegalEntityId(id, legalEntity.getId())
                .map(WorkCalendarService::toResponse)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.WORK_CALENDAR_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<WorkCalendarAssignmentResponse> listAssignments(AuthenticatedUser actor,
                                                                UUID calendarId) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        // Enforce scope: the calendar must be in the caller's legal entity.
        calendarRepository.findByIdAndLegalEntityId(calendarId, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.WORK_CALENDAR_NOT_FOUND));
        return assignmentRepository.findByWorkCalendarIdOrderByEffectiveFromAsc(calendarId).stream()
                .map(WorkCalendarService::toAssignmentResponse)
                .toList();
    }

    // ---- Employee Work Calendar Assignment --------------------------------

    /**
     * Assign a work calendar to an employee for an effective period. The employee
     * is resolved in the caller's scope; the calendar must belong to the
     * employee's legal entity; the new period must not overlap an existing
     * assignment for the employee.
     */
    @Transactional
    public WorkCalendarAssignmentResponse assignToEmployee(AuthenticatedUser actor, UUID employeeId,
                                                           AssignWorkCalendarRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        validateEffectiveRange(request.effectiveFrom(), request.effectiveTo());

        // Calendar must exist within the employee's legal entity (no cross-entity).
        WorkCalendar calendar = calendarRepository
                .findByIdAndLegalEntityId(request.workCalendarId(), employee.getLegalEntityId())
                .orElseThrow(() -> ApiException.conflict(
                        ApiMessages.WORK_CALENDAR_WRONG_LEGAL_ENTITY));

        assertNoAssignmentOverlap(employee.getId(), request.effectiveFrom(), request.effectiveTo());

        WorkCalendarAssignment assignment = new WorkCalendarAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setEmployeeId(employee.getId());
        assignment.setWorkCalendarId(calendar.getId());
        assignment.setEffectiveFrom(request.effectiveFrom());
        assignment.setEffectiveTo(request.effectiveTo());
        assignment.setCreatedBy(actor.userId());
        assignmentRepository.save(assignment);

        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.WORK_CALENDAR_ASSIGNED,
                AuditActions.ENTITY_WORK_CALENDAR_ASSIGNMENT, assignment.getId(),
                "SUCCESS", null, null));
        return toAssignmentResponse(assignment);
    }

    // ---- Validation / scope helpers ---------------------------------------

    private void validateEffectiveRange(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ApiMessages.WORK_CALENDAR_EFFECTIVE_RANGE_INVALID);
        }
    }

    private void validateV0Pattern(boolean mondayToFriday, boolean saturdaySundayWeeklyOff) {
        // v0 business rule: only the standard 5-day pattern is supported. Reject
        // any other configuration rather than silently normalizing it.
        if (!mondayToFriday || !saturdaySundayWeeklyOff) {
            throw ApiException.badRequest(ApiMessages.WORK_CALENDAR_UNSUPPORTED_PATTERN);
        }
    }

    private void assertNoCalendarOverlap(UUID legalEntityId, LocalDate from, LocalDate to,
                                         UUID excludeId) {
        for (WorkCalendar existing : calendarRepository.findByLegalEntityId(legalEntityId)) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            if (rangesOverlap(from, to, existing.getEffectiveFrom(), existing.getEffectiveTo())) {
                throw ApiException.conflict(ApiMessages.WORK_CALENDAR_OVERLAP);
            }
        }
    }

    private void assertNoAssignmentOverlap(UUID employeeId, LocalDate from, LocalDate to) {
        for (WorkCalendarAssignment existing
                : assignmentRepository.findByEmployeeIdOrderByEffectiveFromAsc(employeeId)) {
            if (rangesOverlap(from, to, existing.getEffectiveFrom(), existing.getEffectiveTo())) {
                throw ApiException.conflict(ApiMessages.WORK_CALENDAR_ASSIGNMENT_OVERLAP);
            }
        }
    }

    /**
     * Two effective-dated ranges overlap iff {@code aFrom <= bTo AND bFrom <= aTo},
     * where a null {@code effectiveTo} means open-ended (+infinity). Adjacent
     * ranges (A ends the day before B starts) do NOT overlap; a shared boundary
     * date (A ends the same day B starts) DOES overlap (closed boundary rule).
     */
    private static boolean rangesOverlap(LocalDate aFrom, LocalDate aTo,
                                         LocalDate bFrom, LocalDate bTo) {
        boolean aStartsAfterBEnds = bTo != null && aFrom.isAfter(bTo);
        boolean bStartsAfterAEnds = aTo != null && bFrom.isAfter(aTo);
        return !aStartsAfterBEnds && !bStartsAfterAEnds;
    }

    private LegalEntity resolveScopedLegalEntity(AuthenticatedUser actor) {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        if (!actor.isPlatform() && (actor.companyId() == null
                || !actor.companyId().equals(legalEntity.getCompanyId()))) {
            throw ApiException.notFound(ApiMessages.WORK_CALENDAR_NOT_FOUND);
        }
        return legalEntity;
    }

    private static WorkCalendarResponse toResponse(WorkCalendar c) {
        return new WorkCalendarResponse(c.getId(), c.getLegalEntityId(), c.getName(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.isMondayToFriday(),
                c.isSaturdaySundayWeeklyOff(), c.getStatus().name());
    }

    private static WorkCalendarAssignmentResponse toAssignmentResponse(WorkCalendarAssignment a) {
        return new WorkCalendarAssignmentResponse(a.getId(), a.getEmployeeId(),
                a.getWorkCalendarId(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getCreatedBy());
    }
}
