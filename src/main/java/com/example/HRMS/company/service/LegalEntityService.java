package com.example.HRMS.company.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.dto.LegalEntityDtos.CreateLegalEntityRequest;
import com.example.HRMS.company.dto.LegalEntityDtos.LegalEntityResponse;
import com.example.HRMS.company.dto.LegalEntityDtos.UpdateLegalEntityRequest;
import com.example.HRMS.company.entity.Company;
import com.example.HRMS.company.entity.CompanyStatus;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.repository.CompanyRepository;
import com.example.HRMS.company.repository.LegalEntityRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legal Entity use cases: list, read, create (single active entity per company
 * enforced), and update.
 *
 * <p>v0 supports exactly one active Indian legal entity per company; a second
 * active entity is rejected with a 409 conflict. Legal Entity owns
 * legal-entity identity only — statutory configuration is owned by the statutory
 * module. Material mutations are audited.
 */
@Service
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public LegalEntityService(LegalEntityRepository legalEntityRepository,
                              CompanyRepository companyRepository,
                              AuditService auditService) {
        this.legalEntityRepository = legalEntityRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    /** List legal entities for the v0 company (deterministically ordered). */
    @Transactional(readOnly = true)
    public List<LegalEntityResponse> listLegalEntities() {
        Company company = resolveCompany();
        return legalEntityRepository.findByCompanyIdOrderByLegalNameAsc(company.getId()).stream()
                .map(LegalEntityService::toResponse)
                .toList();
    }

    /**
     * Resolve the single active v0 legal entity for the current company.
     *
     * <p>Exposed as an application-service collaboration point for other modules
     * (e.g. statutory configuration) so they need not reach into the company
     * module's persistence. 404 if no company or active legal entity exists yet.
     */
    @Transactional(readOnly = true)
    public LegalEntity resolveActiveLegalEntity() {
        Company company = resolveCompany();
        return legalEntityRepository
                .findByCompanyIdOrderByLegalNameAsc(company.getId()).stream()
                .filter(e -> e.getStatus() == CompanyStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiMessages.LEGAL_ENTITY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public LegalEntityResponse getLegalEntity(UUID id) {
        return legalEntityRepository.findById(id)
                .map(LegalEntityService::toResponse)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.LEGAL_ENTITY_NOT_FOUND));
    }

    @Transactional
    public LegalEntityResponse createLegalEntity(AuthenticatedUser actor,
                                                 CreateLegalEntityRequest request) {
        Company company = resolveCompany();
        // v0: exactly one active legal entity per company.
        if (legalEntityRepository.existsByCompanyIdAndStatus(company.getId(), CompanyStatus.ACTIVE)) {
            throw ApiException.conflict(ApiMessages.LEGAL_ENTITY_ALREADY_EXISTS);
        }
        LegalEntity entity = new LegalEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompanyId(company.getId());
        entity.setLegalName(request.legalName());
        entity.setCountryCode(request.countryCode());
        entity.setPan(request.pan());
        entity.setFinancialYearStart(request.financialYearStart());
        entity.setStatus(CompanyStatus.ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        legalEntityRepository.save(entity);

        auditService.record(new AuditEvent(actor.userId(), company.getId(), actor.scopeType(),
                AuditActions.LEGAL_ENTITY_CREATED, AuditActions.ENTITY_LEGAL_ENTITY, entity.getId(),
                "SUCCESS", null, null));
        return toResponse(entity);
    }

    @Transactional
    public LegalEntityResponse updateLegalEntity(AuthenticatedUser actor, UUID id,
                                                 UpdateLegalEntityRequest request) {
        LegalEntity entity = legalEntityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.LEGAL_ENTITY_NOT_FOUND));
        entity.setLegalName(request.legalName());
        entity.setCountryCode(request.countryCode());
        entity.setPan(request.pan());
        entity.setFinancialYearStart(request.financialYearStart());
        entity.setStatus(CompanyStatus.valueOf(request.status()));
        entity.setUpdatedAt(LocalDateTime.now());
        legalEntityRepository.save(entity);

        auditService.record(new AuditEvent(actor.userId(), entity.getCompanyId(), actor.scopeType(),
                AuditActions.LEGAL_ENTITY_UPDATED, AuditActions.ENTITY_LEGAL_ENTITY, entity.getId(),
                "SUCCESS", null, null));
        return toResponse(entity);
    }

    private Company resolveCompany() {
        return companyRepository.findFirstByStatus(CompanyStatus.ACTIVE)
                .or(() -> companyRepository.findAll().stream().findFirst())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.COMPANY_NOT_FOUND));
    }

    private static LegalEntityResponse toResponse(LegalEntity e) {
        return new LegalEntityResponse(e.getId(), e.getCompanyId(), e.getLegalName(),
                e.getCountryCode(), e.getPan(), e.getFinancialYearStart(), e.getStatus().name(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
