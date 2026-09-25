package com.example.HRMS.compensation.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.compensation.dto.CompensationDtos.CompensationRequest;
import com.example.HRMS.compensation.dto.CompensationDtos.CompensationResponse;
import com.example.HRMS.compensation.entity.CompensationRecord;
import com.example.HRMS.compensation.entity.CompensationSource;
import com.example.HRMS.compensation.repository.CompensationRecordRepository;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compensation use cases: create the first record, create effective-dated
 * revisions, and read compensation (API §14).
 *
 * <p>The employee is resolved through {@link EmployeeService} scope resolution
 * (company isolation; cross-company access yields 404, no disclosure).
 * Compensation state is immutable: a revision closes the current open-ended
 * record ({@code effective_to = new effectiveFrom − 1 day}) and creates a new
 * open-ended record, atomically. {@code source} is server-controlled;
 * {@code effective_to}, {@code created_by} and {@code created_at} are never
 * accepted from the client. This service performs no payroll/proration
 * calculation. Material create/revision events are audited.
 */
@Service
public class CompensationService {

    private final CompensationRecordRepository repository;
    private final EmployeeService employeeService;
    private final AuditService auditService;

    public CompensationService(CompensationRecordRepository repository,
                               EmployeeService employeeService,
                               AuditService auditService) {
        this.repository = repository;
        this.employeeService = employeeService;
        this.auditService = auditService;
    }

    /** List an employee's compensation records, most recent effective_from first. */
    @Transactional(readOnly = true)
    public List<CompensationResponse> listForEmployee(AuthenticatedUser actor, UUID employeeId) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        return repository.findByEmployeeIdOrderByEffectiveFromDesc(employee.getId()).stream()
                .map(CompensationService::toResponse)
                .toList();
    }

    /** Read one compensation record, enforcing company scope via its employee. */
    @Transactional(readOnly = true)
    public CompensationResponse getById(AuthenticatedUser actor, UUID id) {
        CompensationRecord record = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.COMPENSATION_NOT_FOUND));
        // Enforce scope: the record's employee must be in the caller's scope.
        // resolveScopedEmployee throws 404 (no disclosure) if it is not.
        employeeService.resolveScopedEmployee(actor, record.getEmployeeId());
        return toResponse(record);
    }

    /**
     * Create the first compensation record for an employee. 409 if the employee
     * already has any compensation (use a revision instead). {@code source} is
     * server-assigned MANUAL for this manual API.
     */
    @Transactional
    public CompensationResponse createFirst(AuthenticatedUser actor, UUID employeeId,
                                            CompensationRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        if (repository.existsByEmployeeId(employee.getId())) {
            throw ApiException.conflict(ApiMessages.COMPENSATION_ALREADY_EXISTS);
        }
        CompensationRecord record = newRecord(actor, employee.getId(), request,
                CompensationSource.MANUAL);
        try {
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent first-create: unique invariant protected; surface a conflict.
            throw ApiException.conflict(ApiMessages.COMPENSATION_ALREADY_EXISTS);
        }
        audit(actor, AuditActions.COMPENSATION_CREATED, record.getId());
        return toResponse(record);
    }

    /**
     * Create the first compensation record for an already-resolved employee as
     * part of an atomic CSV import confirmation (V2-006). {@code source} is
     * {@code CSV_IMPORT}. The employee is passed in (it was just created in the
     * same transaction and is trivially in scope), so no re-resolution occurs.
     * Reuses the same first-create rules as {@link #createFirst}: 409 if the
     * employee already has any compensation. The audit participates in the
     * caller's transaction so it rolls back with a failed confirmation.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public CompensationRecord createFirstFromImport(AuthenticatedUser actor, UUID employeeId,
                                                    CompensationRequest request) {
        if (repository.existsByEmployeeId(employeeId)) {
            throw ApiException.conflict(ApiMessages.COMPENSATION_ALREADY_EXISTS);
        }
        CompensationRecord record = newRecord(actor, employeeId, request,
                CompensationSource.CSV_IMPORT);
        repository.save(record);
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.COMPENSATION_CREATED,
                AuditActions.ENTITY_COMPENSATION_RECORD, record.getId(), "SUCCESS", null, null));
        return record;
    }

    /**
     * Create a salary revision: close the current open-ended record and create a
     * new open-ended record, atomically. {@code source} is server-assigned MANUAL.
     */
    @Transactional
    public CompensationResponse createRevision(AuthenticatedUser actor, UUID employeeId,
                                               CompensationRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        CompensationRecord current = repository
                .findByEmployeeIdAndEffectiveToIsNull(employee.getId())
                .orElseThrow(() -> ApiException.conflict(
                        ApiMessages.COMPENSATION_NO_CURRENT_TO_REVISE));

        // New effective_from must be strictly after the current record's start so
        // the closed range [currentFrom, newFrom-1] is valid and non-overlapping.
        // Same-day boundary is non-overlapping, but the revision itself must move
        // forward from the current start date.
        if (!request.effectiveFrom().isAfter(current.getEffectiveFrom())) {
            throw ApiException.conflict(ApiMessages.COMPENSATION_EFFECTIVE_DATE_OVERLAP);
        }

        current.setEffectiveTo(request.effectiveFrom().minusDays(1));
        repository.save(current);

        CompensationRecord revision = newRecord(actor, employee.getId(), request,
                CompensationSource.MANUAL);
        repository.save(revision);

        audit(actor, AuditActions.COMPENSATION_REVISED, revision.getId());
        return toResponse(revision);
    }

    private static CompensationRecord newRecord(AuthenticatedUser actor, UUID employeeId,
                                                CompensationRequest request,
                                                CompensationSource source) {
        CompensationRecord record = new CompensationRecord();
        record.setId(UUID.randomUUID());
        record.setEmployeeId(employeeId);
        record.setEffectiveFrom(request.effectiveFrom());
        record.setEffectiveTo(null);
        record.setCtcMonthly(request.ctcMonthly());
        record.setBasicMonthly(request.basicMonthly());
        record.setHraMonthly(request.hraMonthly());
        record.setDaMonthly(request.daMonthly());
        record.setOtherFixedAllowancesMonthly(request.otherFixedAllowancesMonthly());
        record.setReason(request.reason());
        record.setSource(source);
        record.setCreatedBy(actor.userId());
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private void audit(AuthenticatedUser actor, String action, UUID recordId) {
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                action, AuditActions.ENTITY_COMPENSATION_RECORD, recordId,
                "SUCCESS", null, null));
    }

    private static CompensationResponse toResponse(CompensationRecord c) {
        return new CompensationResponse(c.getId(), c.getEmployeeId(), c.getEffectiveFrom(),
                c.getEffectiveTo(), c.getCtcMonthly(), c.getBasicMonthly(), c.getHraMonthly(),
                c.getDaMonthly(), c.getOtherFixedAllowancesMonthly(), c.getReason(),
                c.getSource() == null ? null : c.getSource().name(),
                c.getCreatedBy(), c.getCreatedAt());
    }
}
