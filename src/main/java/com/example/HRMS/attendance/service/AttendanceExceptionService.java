package com.example.HRMS.attendance.service;

import com.example.HRMS.attendance.dto.AttendanceExceptionDtos.AttendanceExceptionResponse;
import com.example.HRMS.attendance.dto.AttendanceExceptionDtos.UpsertAttendanceExceptionRequest;
import com.example.HRMS.attendance.entity.AttendanceException;
import com.example.HRMS.attendance.entity.AttendanceExceptionType;
import com.example.HRMS.attendance.repository.AttendanceExceptionRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attendance Exception use cases (API spec 12; task V2-008A.4): create, read,
 * list, update and delete admin-entered attendance exceptions.
 *
 * <p>The employee is resolved through {@link EmployeeService} scope resolution
 * (company isolation; cross-company access yields 404, no disclosure). An
 * exception is valid only within the employee's employment period, and at most
 * one may exist per employee per date. {@code exception_type} must be consistent
 * with {@code quantity} (FULL_DAY_ABSENCE=1.0, HALF_DAY=0.5, LOP=0.5/1.0). This
 * service stores INPUT only — it performs no payable-day, LOP-money or payroll
 * calculation, and does not read or mutate leave. It does not resolve the Work
 * Calendar at entry time (scheduled-day/weekly-off interpretation and
 * missing-calendar handling belong to payroll calculation/Health Check per the
 * authoritative contract). Material writes are audited.
 */
@Service
public class AttendanceExceptionService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final AttendanceExceptionRepository repository;
    private final EmployeeService employeeService;
    private final AuditService auditService;

    public AttendanceExceptionService(AttendanceExceptionRepository repository,
                                      EmployeeService employeeService,
                                      AuditService auditService) {
        this.repository = repository;
        this.employeeService = employeeService;
        this.auditService = auditService;
    }

    @Transactional
    public AttendanceExceptionResponse create(AuthenticatedUser actor,
                                              UpsertAttendanceExceptionRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, request.employeeId());
        AttendanceExceptionType type = AttendanceExceptionType.valueOf(request.exceptionType());
        validateEmploymentPeriod(employee, request.attendanceDate());
        validateQuantity(type, request.quantity());
        if (repository.findByEmployeeIdAndAttendanceDate(
                employee.getId(), request.attendanceDate()).isPresent()) {
            throw ApiException.conflict(ApiMessages.ATTENDANCE_EXCEPTION_DUPLICATE);
        }

        AttendanceException exception = new AttendanceException();
        exception.setId(UUID.randomUUID());
        exception.setEmployeeId(employee.getId());
        exception.setAttendanceDate(request.attendanceDate());
        exception.setExceptionType(type);
        exception.setQuantity(request.quantity());
        exception.setReason(request.reason());
        exception.setCreatedBy(actor.userId());
        exception.setCreatedAt(LocalDateTime.now());
        try {
            repository.saveAndFlush(exception);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(ApiMessages.ATTENDANCE_EXCEPTION_DUPLICATE);
        }
        audit(actor, AuditActions.ATTENDANCE_EXCEPTION_CREATED, exception.getId());
        return toResponse(exception);
    }

    @Transactional
    public AttendanceExceptionResponse update(AuthenticatedUser actor, UUID id,
                                              UpsertAttendanceExceptionRequest request) {
        AttendanceException exception = resolveScoped(actor, id);
        // employee_id is immutable: the request must reference the same employee
        // that owns the exception (no re-owning to another employee).
        Employee employee = employeeService.resolveScopedEmployee(actor, request.employeeId());
        if (!employee.getId().equals(exception.getEmployeeId())) {
            throw ApiException.badRequest(ApiMessages.ATTENDANCE_EXCEPTION_NOT_FOUND);
        }
        AttendanceExceptionType type = AttendanceExceptionType.valueOf(request.exceptionType());
        validateEmploymentPeriod(employee, request.attendanceDate());
        validateQuantity(type, request.quantity());
        // A different date must not collide with another existing exception.
        repository.findByEmployeeIdAndAttendanceDate(employee.getId(), request.attendanceDate())
                .filter(other -> !other.getId().equals(exception.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict(ApiMessages.ATTENDANCE_EXCEPTION_DUPLICATE);
                });

        exception.setAttendanceDate(request.attendanceDate());
        exception.setExceptionType(type);
        exception.setQuantity(request.quantity());
        exception.setReason(request.reason());
        repository.save(exception);
        audit(actor, AuditActions.ATTENDANCE_EXCEPTION_UPDATED, exception.getId());
        return toResponse(exception);
    }

    @Transactional(readOnly = true)
    public AttendanceExceptionResponse get(AuthenticatedUser actor, UUID id) {
        return toResponse(resolveScoped(actor, id));
    }

    /** List an employee's attendance exceptions (paginated, scope-enforced). */
    @Transactional(readOnly = true)
    public Page<AttendanceExceptionResponse> listForEmployee(AuthenticatedUser actor,
                                                             UUID employeeId, Pageable pageable) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        return repository.findByEmployeeId(employee.getId(), pageable)
                .map(AttendanceExceptionService::toResponse);
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        AttendanceException exception = resolveScoped(actor, id);
        repository.delete(exception);
        audit(actor, AuditActions.ATTENDANCE_EXCEPTION_DELETED, exception.getId());
    }

    /** Load an exception and enforce that its employee is in the caller's scope. */
    private AttendanceException resolveScoped(AuthenticatedUser actor, UUID id) {
        AttendanceException exception = repository.findById(id)
                .orElseThrow(() ->
                        ApiException.notFound(ApiMessages.ATTENDANCE_EXCEPTION_NOT_FOUND));
        // resolveScopedEmployee throws 404 (no disclosure) if the owning employee
        // is not in the caller's company scope.
        employeeService.resolveScopedEmployee(actor, exception.getEmployeeId());
        return exception;
    }

    private void validateEmploymentPeriod(Employee employee, LocalDate date) {
        if (date.isBefore(employee.getJoiningDate())
                || (employee.getExitDate() != null && date.isAfter(employee.getExitDate()))) {
            throw ApiException.badRequest(ApiMessages.ATTENDANCE_DATE_OUTSIDE_EMPLOYMENT);
        }
    }

    /**
     * Enforce the authoritative type↔quantity relationship: FULL_DAY_ABSENCE=1.0,
     * HALF_DAY=0.5, LOP∈{0.5,1.0}. v0 restricts day quantities to 0.5 or 1.0.
     */
    private void validateQuantity(AttendanceExceptionType type, BigDecimal quantity) {
        boolean valid = switch (type) {
            case FULL_DAY_ABSENCE -> quantity.compareTo(ONE) == 0;
            case HALF_DAY -> quantity.compareTo(HALF) == 0;
            case LOP -> quantity.compareTo(HALF) == 0 || quantity.compareTo(ONE) == 0;
        };
        if (!valid) {
            throw ApiException.badRequest(ApiMessages.ATTENDANCE_QUANTITY_INVALID);
        }
    }

    private void audit(AuthenticatedUser actor, String action, UUID entityId) {
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), action, AuditActions.ENTITY_ATTENDANCE_EXCEPTION, entityId,
                "SUCCESS", null, null));
    }

    private static AttendanceExceptionResponse toResponse(AttendanceException e) {
        return new AttendanceExceptionResponse(e.getId(), e.getEmployeeId(), e.getAttendanceDate(),
                e.getExceptionType().name(), e.getQuantity(), e.getReason(), e.getCreatedBy(),
                e.getCreatedAt());
    }
}
