package com.example.HRMS.company.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.dto.CompanyDtos.CompanyResponse;
import com.example.HRMS.company.dto.CompanyDtos.CreateCompanyRequest;
import com.example.HRMS.company.dto.CompanyDtos.UpdateCompanyRequest;
import com.example.HRMS.company.entity.Company;
import com.example.HRMS.company.entity.CompanyStatus;
import com.example.HRMS.company.repository.CompanyRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company use cases: read, create (single active company enforced), and update.
 *
 * <p>v0 supports exactly one active company. Creating a second active company is
 * rejected with a 409 conflict via the shared error contract. The "single active
 * company" rule is enforced here (service layer), keeping the schema extensible
 * for a future multi-company platform. Material mutations are audited.
 */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public CompanyService(CompanyRepository companyRepository, AuditService auditService) {
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    /**
     * Read the single active v0 company. 404 if none has been created yet.
     *
     * <p>Resolution is by the active company only. It deliberately does not fall
     * back to an arbitrary inactive row: in the single-company v0 model the
     * authoritative company is the active one, and an inactive row is not a
     * substitute for "the company".
     */
    @Transactional(readOnly = true)
    public CompanyResponse getCompany() {
        Company company = companyRepository.findFirstByStatus(CompanyStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.COMPANY_NOT_FOUND));
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse createCompany(AuthenticatedUser actor, CreateCompanyRequest request) {
        // v0: exactly one active company.
        if (companyRepository.existsByStatus(CompanyStatus.ACTIVE)) {
            throw ApiException.conflict(ApiMessages.COMPANY_ALREADY_EXISTS);
        }
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName(request.name());
        company.setStatus(CompanyStatus.ACTIVE);
        company.setCreatedAt(LocalDateTime.now());
        company.setUpdatedAt(LocalDateTime.now());
        companyRepository.save(company);

        // Audit runs in REQUIRES_NEW; company_id must reference an already-committed
        // company (the actor's tenant scope), never the row being created in the
        // still-open outer transaction. The created company is identified by entityId.
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.COMPANY_CREATED, AuditActions.ENTITY_COMPANY, company.getId(),
                "SUCCESS", null, null));
        return toResponse(company);
    }

    /** Update the single v0 company (the API contract is singular {@code PUT /company}). */
    @Transactional
    public CompanyResponse updateCompany(AuthenticatedUser actor, UpdateCompanyRequest request) {
        Company company = companyRepository.findFirstByStatus(CompanyStatus.ACTIVE)
                .or(() -> companyRepository.findAll().stream().findFirst())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.COMPANY_NOT_FOUND));
        company.setName(request.name());
        company.setStatus(CompanyStatus.valueOf(request.status()));
        company.setUpdatedAt(LocalDateTime.now());
        companyRepository.save(company);

        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                AuditActions.COMPANY_UPDATED, AuditActions.ENTITY_COMPANY, company.getId(),
                "SUCCESS", null, null));
        return toResponse(company);
    }

    private static CompanyResponse toResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getStatus().name(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
