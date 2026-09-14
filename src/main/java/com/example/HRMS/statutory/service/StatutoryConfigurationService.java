package com.example.HRMS.statutory.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.company.entity.LegalEntity;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.statutory.dto.StatutoryDtos.StatutoryConfigurationResponse;
import com.example.HRMS.statutory.dto.StatutoryDtos.UpsertStatutoryConfigurationRequest;
import com.example.HRMS.statutory.entity.PfApplicability;
import com.example.HRMS.statutory.entity.PfRegistrationStatus;
import com.example.HRMS.statutory.entity.StatutoryConfiguration;
import com.example.HRMS.statutory.entity.TdsPolicy;
import com.example.HRMS.statutory.repository.StatutoryConfigurationRepository;
import com.example.HRMS.statutory.repository.StatutoryRuleVersionSetRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statutory configuration use cases: read and create/update the effective
 * statutory applicability/policy for the single v0 legal entity (API spec §8).
 *
 * <p>Applicability is stored <em>explicitly</em>; the service never infers it or
 * silently defaults {@code UNCONFIRMED}. It never stores or computes statutory
 * rates — it references a verified {@link com.example.HRMS.statutory.entity.StatutoryRuleVersionSet}
 * by id. PT state is validated against the supported v0 set so an unsupported
 * state is rejected rather than silently accepted. Downstream Health
 * Check/blocking behavior lives in the payroll module and is out of scope here.
 * Material configuration changes are audited.
 */
@Service
public class StatutoryConfigurationService {

    private final StatutoryConfigurationRepository configurationRepository;
    private final StatutoryRuleVersionSetRepository ruleVersionSetRepository;
    private final LegalEntityService legalEntityService;
    private final AuditService auditService;

    public StatutoryConfigurationService(StatutoryConfigurationRepository configurationRepository,
                                         StatutoryRuleVersionSetRepository ruleVersionSetRepository,
                                         LegalEntityService legalEntityService,
                                         AuditService auditService) {
        this.configurationRepository = configurationRepository;
        this.ruleVersionSetRepository = ruleVersionSetRepository;
        this.legalEntityService = legalEntityService;
        this.auditService = auditService;
    }

    /** Read the current effective statutory configuration. 404 if none set yet. */
    @Transactional(readOnly = true)
    public StatutoryConfigurationResponse getConfiguration() {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();
        StatutoryConfiguration config = configurationRepository
                .findFirstByLegalEntityIdAndEffectiveToIsNull(legalEntity.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_CONFIG_NOT_FOUND));
        return toResponse(config);
    }

    /**
     * Create or update the current effective statutory configuration for the v0
     * legal entity. If a current (open-ended) configuration exists it is updated
     * in place; otherwise a new one is created. Returns the persisted state.
     */
    @Transactional
    public StatutoryConfigurationResponse upsertConfiguration(
            AuthenticatedUser actor, UpsertStatutoryConfigurationRequest request) {
        LegalEntity legalEntity = legalEntityService.resolveActiveLegalEntity();

        PfApplicability pfApplicability = PfApplicability.valueOf(request.pfApplicability());
        PfRegistrationStatus pfRegistrationStatus = request.pfRegistrationStatus() == null
                ? null : PfRegistrationStatus.valueOf(request.pfRegistrationStatus());
        TdsPolicy tdsPolicy = TdsPolicy.valueOf(request.tdsPolicy());

        validateEffectiveRange(request);
        validateRuleVersionSet(request.ruleVersionSetId());
        validatePtState(request.ptState());
        validatePfRegistration(pfApplicability, pfRegistrationStatus, request.pfRegistrationNumber());

        boolean creating = configurationRepository
                .findFirstByLegalEntityIdAndEffectiveToIsNull(legalEntity.getId()).isEmpty();

        StatutoryConfiguration config = configurationRepository
                .findFirstByLegalEntityIdAndEffectiveToIsNull(legalEntity.getId())
                .orElseGet(StatutoryConfiguration::new);
        if (creating) {
            config.setId(UUID.randomUUID());
            config.setLegalEntityId(legalEntity.getId());
            config.setCreatedBy(actor.userId());
            config.setCreatedAt(LocalDateTime.now());
        }
        config.setEffectiveFrom(request.effectiveFrom());
        config.setEffectiveTo(request.effectiveTo());
        config.setPfApplicability(pfApplicability);
        config.setPfRegistrationStatus(pfRegistrationStatus);
        config.setPfRegistrationNumber(request.pfRegistrationNumber());
        config.setPtState(request.ptState());
        config.setTdsPolicy(tdsPolicy);
        config.setRuleVersionSetId(request.ruleVersionSetId());
        configurationRepository.save(config);

        // Audit runs in REQUIRES_NEW; company_id references the actor's tenant
        // (an already-committed company, or null for a platform actor), never a
        // row created in the still-open outer transaction. The configuration is
        // identified by entityId.
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                creating ? AuditActions.STATUTORY_CONFIG_CREATED : AuditActions.STATUTORY_CONFIG_UPDATED,
                AuditActions.ENTITY_STATUTORY_CONFIGURATION, config.getId(),
                "SUCCESS", null, null));
        return toResponse(config);
    }

    private void validateEffectiveRange(UpsertStatutoryConfigurationRequest request) {
        if (request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw ApiException.badRequest(ApiMessages.STATUTORY_EFFECTIVE_RANGE_INVALID);
        }
    }

    private void validateRuleVersionSet(UUID ruleVersionSetId) {
        if (!ruleVersionSetRepository.existsById(ruleVersionSetId)) {
            throw ApiException.badRequest(ApiMessages.STATUTORY_RULE_VERSION_SET_REQUIRED);
        }
    }

    private void validatePtState(String ptState) {
        // PT state is optional in the model, but if one is provided it must be a
        // supported v0 state. An unsupported state is never silently accepted.
        if (ptState != null && !ptState.isBlank() && !SupportedPtStates.isSupported(ptState)) {
            throw ApiException.badRequest(ApiMessages.ptStateUnsupported(ptState));
        }
    }

    private void validatePfRegistration(PfApplicability applicability,
                                        PfRegistrationStatus registrationStatus,
                                        String registrationNumber) {
        // Applicability and registration are separate fields (Business Rules 7.1).
        // When PF is applicable, a registration status must be stated explicitly;
        // an unresolved registration remains a downstream blocking Health Check
        // concern, but the field itself must be present. When PF is not applicable
        // or unconfirmed, registration details must not be asserted.
        if (applicability == PfApplicability.YES) {
            if (registrationStatus == null) {
                throw ApiException.badRequest(ApiMessages.STATUTORY_PF_REGISTRATION_REQUIRED);
            }
        } else if (registrationStatus != null
                || (registrationNumber != null && !registrationNumber.isBlank())) {
            throw ApiException.badRequest(ApiMessages.STATUTORY_PF_REGISTRATION_NOT_ALLOWED);
        }
    }

    private static StatutoryConfigurationResponse toResponse(StatutoryConfiguration c) {
        return new StatutoryConfigurationResponse(c.getId(), c.getLegalEntityId(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.getPfApplicability().name(),
                c.getPfRegistrationStatus() == null ? null : c.getPfRegistrationStatus().name(),
                c.getPfRegistrationNumber(), c.getPtState(), c.getTdsPolicy().name(),
                c.getRuleVersionSetId(), c.getCreatedBy(), c.getCreatedAt());
    }
}
