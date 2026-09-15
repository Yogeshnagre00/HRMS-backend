package com.example.HRMS.employee.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.example.HRMS.employee.dto.EmployeeDtos.EmployeeResponse;
import com.example.HRMS.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.entity.EmployeeStatus;
import com.example.HRMS.employee.entity.TaxRegime;
import com.example.HRMS.employee.repository.EmployeeRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee master use cases: create, read, list (paginated) and update (API
 * spec §10). Enforces company/legal-entity isolation server-side, uniqueness of
 * the business Employee ID within the legal entity, employment-period validity,
 * and audits material mutations.
 *
 * <p>Scope resolution: every operation binds to the caller's legal entity (the
 * single active v0 legal entity), and a company-scoped caller is confined to its
 * own company. Reads are query-scoped by {@code legal_entity_id} so another
 * company's employee cannot be loaded and its existence is never disclosed.
 * Ownership ({@code legal_entity_id}) and the business {@code employeeId} are not
 * mutable via update. No future-domain (bank/tax/leave/calendar) records are
 * touched.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final LegalEntityService legalEntityService;
    private final AuditService auditService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           LegalEntityService legalEntityService,
                           AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.legalEntityService = legalEntityService;
        this.auditService = auditService;
    }

    @Transactional
    public EmployeeResponse createEmployee(AuthenticatedUser actor, CreateEmployeeRequest request) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        validateEmploymentPeriod(request.joiningDate(), request.exitDate());
        if (employeeRepository.existsByLegalEntityIdAndEmployeeId(
                legalEntity.getId(), request.employeeId())) {
            throw ApiException.conflict(ApiMessages.EMPLOYEE_ID_ALREADY_EXISTS);
        }

        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setLegalEntityId(legalEntity.getId());
        employee.setEmployeeId(request.employeeId());
        applyMutableFields(employee, request.fullName(), request.joiningDate(), request.exitDate(),
                request.employmentType(), request.department(), request.designation(),
                request.location(), request.pan(), request.uan(), request.ptState(),
                TaxRegime.valueOf(request.taxRegime()), EmployeeStatus.valueOf(request.status()));
        LocalDateTime now = LocalDateTime.now();
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        employeeRepository.save(employee);

        // Audit runs in REQUIRES_NEW; company_id references the actor's tenant
        // (already committed, or null for a platform actor), never a row created
        // in the still-open transaction. The employee is identified by entityId.
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.EMPLOYEE_CREATED, AuditActions.ENTITY_EMPLOYEE, employee.getId(),
                "SUCCESS", null, null));
        return toResponse(employee);
    }

    /**
     * Resolve the caller's active legal entity, enforcing company isolation.
     * Exposed as a collaboration point for the V2-006 CSV import confirmation,
     * which resolves scope once and then creates several employees + owned records
     * in a single atomic transaction.
     */
    @Transactional(readOnly = true)
    public LegalEntity resolveScopedLegalEntityForImport(AuthenticatedUser actor) {
        return resolveScopedLegalEntity(actor);
    }

    /**
     * Report whether an Employee ID already exists within a legal entity. Used by
     * the V2-006 create-only confirmation to detect conflicts before persisting
     * any business data (existing Employee ID blocks the entire import).
     */
    @Transactional(readOnly = true)
    public boolean employeeIdExists(UUID legalEntityId, String employeeId) {
        return employeeRepository.existsByLegalEntityIdAndEmployeeId(legalEntityId, employeeId);
    }

    /**
     * Create an employee within an already-resolved legal entity as part of an
     * atomic CSV import confirmation (V2-006). CSV confirmation is CREATE-ONLY:
     * an existing Employee ID is a conflict (409) and never an update. Reuses the
     * same employment-period validation, field mapping and uniqueness rule as
     * {@link #createEmployee}. The audit participates in the caller's transaction
     * so it rolls back with a failed confirmation.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Employee createEmployeeFromImport(AuthenticatedUser actor, LegalEntity legalEntity,
                                             CreateEmployeeRequest request) {
        validateEmploymentPeriod(request.joiningDate(), request.exitDate());
        if (employeeRepository.existsByLegalEntityIdAndEmployeeId(
                legalEntity.getId(), request.employeeId())) {
            throw ApiException.conflict(ApiMessages.EMPLOYEE_ID_ALREADY_EXISTS);
        }
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setLegalEntityId(legalEntity.getId());
        employee.setEmployeeId(request.employeeId());
        applyMutableFields(employee, request.fullName(), request.joiningDate(), request.exitDate(),
                request.employmentType(), request.department(), request.designation(),
                request.location(), request.pan(), request.uan(), request.ptState(),
                TaxRegime.valueOf(request.taxRegime()), EmployeeStatus.valueOf(request.status()));
        LocalDateTime now = LocalDateTime.now();
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        employeeRepository.save(employee);
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), AuditActions.EMPLOYEE_CREATED, AuditActions.ENTITY_EMPLOYEE,
                employee.getId(), "SUCCESS", null, null));
        return employee;
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(AuthenticatedUser actor, UUID id) {
        return toResponse(resolveScopedEmployee(actor, id));
    }

    /**
     * Resolve an employee that the caller is permitted to access, enforcing
     * company isolation, or throw 404 (no cross-company disclosure). Exposed as
     * an application-service collaboration point for sibling employee-owned
     * modules (e.g. bank details) so they reuse one scope-resolution path.
     */
    @Transactional(readOnly = true)
    public Employee resolveScopedEmployee(AuthenticatedUser actor, UUID id) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        return employeeRepository.findByIdAndLegalEntityId(id, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.EMPLOYEE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> listEmployees(AuthenticatedUser actor, Pageable pageable) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        return employeeRepository.findByLegalEntityId(legalEntity.getId(), pageable)
                .map(EmployeeService::toResponse);
    }

    @Transactional
    public EmployeeResponse updateEmployee(AuthenticatedUser actor, UUID id,
                                           UpdateEmployeeRequest request) {
        LegalEntity legalEntity = resolveScopedLegalEntity(actor);
        Employee employee = employeeRepository.findByIdAndLegalEntityId(id, legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.EMPLOYEE_NOT_FOUND));
        validateEmploymentPeriod(request.joiningDate(), request.exitDate());

        applyMutableFields(employee, request.fullName(), request.joiningDate(), request.exitDate(),
                request.employmentType(), request.department(), request.designation(),
                request.location(), request.pan(), request.uan(), request.ptState(),
                TaxRegime.valueOf(request.taxRegime()), EmployeeStatus.valueOf(request.status()));
        employee.setUpdatedAt(LocalDateTime.now());
        employeeRepository.save(employee);

        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.EMPLOYEE_UPDATED, AuditActions.ENTITY_EMPLOYEE, employee.getId(),
                "SUCCESS", null, null));
        return toResponse(employee);
    }

    /**
     * Resolve the legal entity the caller may operate on, enforcing company
     * isolation. v0 has one active legal entity; a company-scoped caller must
     * belong to that entity's company, otherwise the resource is treated as not
     * found (no cross-company disclosure). Platform actors are unrestricted.
     */
    private LegalEntity resolveScopedLegalEntity(AuthenticatedUser actor) {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        if (!actor.isPlatform()
                && (actor.companyId() == null
                    || !actor.companyId().equals(legalEntity.getCompanyId()))) {
            throw ApiException.notFound(ApiMessages.EMPLOYEE_NOT_FOUND);
        }
        return legalEntity;
    }

    private void validateEmploymentPeriod(java.time.LocalDate joining, java.time.LocalDate exit) {
        if (exit != null && exit.isBefore(joining)) {
            throw ApiException.badRequest(ApiMessages.EMPLOYEE_EXIT_BEFORE_JOINING);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void applyMutableFields(Employee e, String fullName, java.time.LocalDate joining,
                                           java.time.LocalDate exit, String employmentType,
                                           String department, String designation, String location,
                                           String pan, String uan, String ptState,
                                           TaxRegime taxRegime, EmployeeStatus status) {
        e.setFullName(fullName);
        e.setJoiningDate(joining);
        e.setExitDate(exit);
        e.setEmploymentType(employmentType);
        e.setDepartment(department);
        e.setDesignation(designation);
        e.setLocation(location);
        e.setPan(pan);
        e.setUan(uan);
        e.setPtState(ptState);
        e.setTaxRegime(taxRegime);
        e.setStatus(status);
    }

    private static EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(e.getId(), e.getLegalEntityId(), e.getEmployeeId(),
                e.getFullName(), e.getJoiningDate(), e.getExitDate(), e.getEmploymentType(),
                e.getDepartment(), e.getDesignation(), e.getLocation(), e.getPan(), e.getUan(),
                e.getPtState(), e.getTaxRegime().name(), e.getStatus().name(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
