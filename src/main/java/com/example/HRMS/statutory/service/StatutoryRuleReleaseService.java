package com.example.HRMS.statutory.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.statutory.dto.StatutoryDtos.CreatePfRuleRequest;
import com.example.HRMS.statutory.dto.StatutoryDtos.CreatePtRuleRequest;
import com.example.HRMS.statutory.dto.StatutoryDtos.CreateTdsRuleRequest;
import com.example.HRMS.statutory.dto.StatutoryDtos.StatutoryRuleResponse;
import com.example.HRMS.statutory.dto.StatutoryDtos.UpdateDraftRuleRequest;
import com.example.HRMS.statutory.entity.PtRulePeriodicity;
import com.example.HRMS.statutory.entity.PtRuleState;
import com.example.HRMS.statutory.entity.RuleVersionSetStatus;
import com.example.HRMS.statutory.entity.StatutoryPfRule;
import com.example.HRMS.statutory.entity.StatutoryPtRule;
import com.example.HRMS.statutory.entity.StatutoryRuleType;
import com.example.HRMS.statutory.entity.StatutoryTdsRule;
import com.example.HRMS.statutory.repository.StatutoryPfRuleRepository;
import com.example.HRMS.statutory.repository.StatutoryPtRuleRepository;
import com.example.HRMS.statutory.repository.StatutoryRuleVersionSetRepository;
import com.example.HRMS.statutory.repository.StatutoryTdsRuleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Versioned statutory rule-value store use cases (Phase 2). Manages the PF/PT/TDS
 * rule datasets bound to a {@link com.example.HRMS.statutory.entity.StatutoryRuleVersionSet}
 * through their DRAFT → VERIFIED → SUPERSEDED lifecycle.
 *
 * <p><b>No statutory values are created by this service.</b> A rule's concrete
 * values live in an opaque {@code rulePayload} (JSON) supplied by the release
 * authority; the server never fabricates or defaults them.
 *
 * <p><b>Verification gate.</b> A rule may become {@code VERIFIED} only if it is
 * currently {@code DRAFT}, has a valid effective range, a non-empty valid-JSON
 * payload, complete source metadata (authority, source document, source URL,
 * verification date), and does not overlap an existing {@code VERIFIED} rule for
 * the same jurisdiction/state/FY. {@code VERIFIED}/{@code SUPERSEDED} rules are
 * immutable (edits and re-verification are rejected). This service does not run
 * PF/PT/TDS calculations.
 */
@Service
public class StatutoryRuleReleaseService {

    private final StatutoryRuleVersionSetRepository versionSetRepository;
    private final StatutoryPfRuleRepository pfRepository;
    private final StatutoryPtRuleRepository ptRepository;
    private final StatutoryTdsRuleRepository tdsRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public StatutoryRuleReleaseService(StatutoryRuleVersionSetRepository versionSetRepository,
                                       StatutoryPfRuleRepository pfRepository,
                                       StatutoryPtRuleRepository ptRepository,
                                       StatutoryTdsRuleRepository tdsRepository,
                                       AuditService auditService,
                                       ObjectMapper objectMapper) {
        this.versionSetRepository = versionSetRepository;
        this.pfRepository = pfRepository;
        this.ptRepository = ptRepository;
        this.tdsRepository = tdsRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ---- PF -------------------------------------------------------------

    @Transactional
    public StatutoryRuleResponse createPfDraft(AuthenticatedUser actor, CreatePfRuleRequest req) {
        requireVersionSet(req.ruleVersionSetId());
        requireEffectiveRange(req.effectiveFrom(), req.effectiveTo());

        StatutoryPfRule rule = new StatutoryPfRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleVersionSetId(req.ruleVersionSetId());
        rule.setRuleType(StatutoryRuleType.PF);
        rule.setJurisdiction(req.jurisdiction());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setAuthority(req.authority());
        rule.setSourceDocument(req.sourceDocument());
        rule.setSourceUrl(req.sourceUrl());
        rule.setVerificationDate(req.verificationDate());
        rule.setReleaseStatus(RuleVersionSetStatus.DRAFT);
        rule.setRulePayload(validatedPayloadOrNull(req.rulePayload()));
        rule.setCreatedBy(actor.userId());
        rule.setCreatedAt(LocalDateTime.now());
        savePf(rule);
        audit(actor, AuditActions.STATUTORY_RULE_CREATED, AuditActions.ENTITY_STATUTORY_PF_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse updatePfDraft(AuthenticatedUser actor, UUID id,
                                               UpdateDraftRuleRequest req) {
        StatutoryPfRule rule = pfRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireDraft(rule.getReleaseStatus());
        requireEffectiveRange(req.effectiveFrom(), req.effectiveTo());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setAuthority(req.authority());
        rule.setSourceDocument(req.sourceDocument());
        rule.setSourceUrl(req.sourceUrl());
        rule.setVerificationDate(req.verificationDate());
        rule.setRulePayload(validatedPayloadOrNull(req.rulePayload()));
        pfRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_UPDATED, AuditActions.ENTITY_STATUTORY_PF_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse verifyPf(AuthenticatedUser actor, UUID id) {
        StatutoryPfRule rule = pfRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireDraftForVerify(rule.getReleaseStatus());
        requireCompleteForVerify(rule.getAuthority(), rule.getSourceDocument(), rule.getSourceUrl(),
                rule.getVerificationDate(), rule.getRulePayload());
        // Overlap against other VERIFIED PF rules for the same jurisdiction.
        List<StatutoryPfRule> verified = pfRepository.findByJurisdictionAndReleaseStatus(
                rule.getJurisdiction(), RuleVersionSetStatus.VERIFIED);
        for (StatutoryPfRule other : verified) {
            if (!other.getId().equals(rule.getId())
                    && overlaps(rule.getEffectiveFrom(), rule.getEffectiveTo(),
                    other.getEffectiveFrom(), other.getEffectiveTo())) {
                throw ApiException.conflict(ApiMessages.STATUTORY_RULE_PERIOD_OVERLAP);
            }
        }
        rule.setReleaseStatus(RuleVersionSetStatus.VERIFIED);
        rule.setVerifiedBy(actor.userId());
        rule.setVerifiedAt(LocalDateTime.now());
        pfRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_VERIFIED, AuditActions.ENTITY_STATUTORY_PF_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse supersedePf(AuthenticatedUser actor, UUID id) {
        StatutoryPfRule rule = pfRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireVerifiedForSupersede(rule.getReleaseStatus());
        rule.setReleaseStatus(RuleVersionSetStatus.SUPERSEDED);
        pfRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_SUPERSEDED, AuditActions.ENTITY_STATUTORY_PF_RULE,
                rule.getId());
        return toResponse(rule);
    }

    private void savePf(StatutoryPfRule rule) {
        try {
            pfRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(ApiMessages.STATUTORY_RULE_ALREADY_EXISTS);
        }
    }

    // ---- PT -------------------------------------------------------------

    @Transactional
    public StatutoryRuleResponse createPtDraft(AuthenticatedUser actor, CreatePtRuleRequest req) {
        requireVersionSet(req.ruleVersionSetId());
        requireEffectiveRange(req.effectiveFrom(), req.effectiveTo());

        StatutoryPtRule rule = new StatutoryPtRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleVersionSetId(req.ruleVersionSetId());
        rule.setRuleType(StatutoryRuleType.PT);
        rule.setJurisdiction(req.jurisdiction());
        rule.setPtState(PtRuleState.valueOf(req.ptState()));
        rule.setLocalBody(req.localBody());
        rule.setPeriodicity(PtRulePeriodicity.valueOf(req.periodicity()));
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setAuthority(req.authority());
        rule.setSourceDocument(req.sourceDocument());
        rule.setSourceUrl(req.sourceUrl());
        rule.setVerificationDate(req.verificationDate());
        rule.setReleaseStatus(RuleVersionSetStatus.DRAFT);
        rule.setRulePayload(validatedPayloadOrNull(req.rulePayload()));
        rule.setCreatedBy(actor.userId());
        rule.setCreatedAt(LocalDateTime.now());
        savePt(rule);
        audit(actor, AuditActions.STATUTORY_RULE_CREATED, AuditActions.ENTITY_STATUTORY_PT_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse updatePtDraft(AuthenticatedUser actor, UUID id,
                                               UpdateDraftRuleRequest req) {
        StatutoryPtRule rule = ptRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireDraft(rule.getReleaseStatus());
        requireEffectiveRange(req.effectiveFrom(), req.effectiveTo());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setAuthority(req.authority());
        rule.setSourceDocument(req.sourceDocument());
        rule.setSourceUrl(req.sourceUrl());
        rule.setVerificationDate(req.verificationDate());
        rule.setRulePayload(validatedPayloadOrNull(req.rulePayload()));
        ptRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_UPDATED, AuditActions.ENTITY_STATUTORY_PT_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse verifyPt(AuthenticatedUser actor, UUID id) {
        StatutoryPtRule rule = ptRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireDraftForVerify(rule.getReleaseStatus());
        requireCompleteForVerify(rule.getAuthority(), rule.getSourceDocument(), rule.getSourceUrl(),
                rule.getVerificationDate(), rule.getRulePayload());
        List<StatutoryPtRule> verified = ptRepository.findByPtStateAndReleaseStatus(
                rule.getPtState(), RuleVersionSetStatus.VERIFIED);
        for (StatutoryPtRule other : verified) {
            if (!other.getId().equals(rule.getId())
                    && overlaps(rule.getEffectiveFrom(), rule.getEffectiveTo(),
                    other.getEffectiveFrom(), other.getEffectiveTo())) {
                throw ApiException.conflict(ApiMessages.STATUTORY_RULE_PERIOD_OVERLAP);
            }
        }
        rule.setReleaseStatus(RuleVersionSetStatus.VERIFIED);
        rule.setVerifiedBy(actor.userId());
        rule.setVerifiedAt(LocalDateTime.now());
        ptRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_VERIFIED, AuditActions.ENTITY_STATUTORY_PT_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse supersedePt(AuthenticatedUser actor, UUID id) {
        StatutoryPtRule rule = ptRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireVerifiedForSupersede(rule.getReleaseStatus());
        rule.setReleaseStatus(RuleVersionSetStatus.SUPERSEDED);
        ptRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_SUPERSEDED, AuditActions.ENTITY_STATUTORY_PT_RULE,
                rule.getId());
        return toResponse(rule);
    }

    private void savePt(StatutoryPtRule rule) {
        try {
            ptRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(ApiMessages.STATUTORY_RULE_ALREADY_EXISTS);
        }
    }

    // ---- TDS ------------------------------------------------------------

    @Transactional
    public StatutoryRuleResponse createTdsDraft(AuthenticatedUser actor, CreateTdsRuleRequest req) {
        requireVersionSet(req.ruleVersionSetId());
        requireEffectiveRange(req.effectiveFrom(), req.effectiveTo());

        StatutoryTdsRule rule = new StatutoryTdsRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleVersionSetId(req.ruleVersionSetId());
        rule.setRuleType(StatutoryRuleType.TDS);
        rule.setJurisdiction(req.jurisdiction());
        rule.setFinancialYear(req.financialYear());
        rule.setTaxRegime(req.taxRegime());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setAuthority(req.authority());
        rule.setSourceDocument(req.sourceDocument());
        rule.setSourceUrl(req.sourceUrl());
        rule.setVerificationDate(req.verificationDate());
        rule.setReleaseStatus(RuleVersionSetStatus.DRAFT);
        rule.setRulePayload(validatedPayloadOrNull(req.rulePayload()));
        rule.setCreatedBy(actor.userId());
        rule.setCreatedAt(LocalDateTime.now());
        saveTds(rule);
        audit(actor, AuditActions.STATUTORY_RULE_CREATED, AuditActions.ENTITY_STATUTORY_TDS_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse updateTdsDraft(AuthenticatedUser actor, UUID id,
                                                UpdateDraftRuleRequest req) {
        StatutoryTdsRule rule = tdsRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireDraft(rule.getReleaseStatus());
        requireEffectiveRange(req.effectiveFrom(), req.effectiveTo());
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setAuthority(req.authority());
        rule.setSourceDocument(req.sourceDocument());
        rule.setSourceUrl(req.sourceUrl());
        rule.setVerificationDate(req.verificationDate());
        rule.setRulePayload(validatedPayloadOrNull(req.rulePayload()));
        tdsRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_UPDATED, AuditActions.ENTITY_STATUTORY_TDS_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse verifyTds(AuthenticatedUser actor, UUID id) {
        StatutoryTdsRule rule = tdsRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireDraftForVerify(rule.getReleaseStatus());
        requireCompleteForVerify(rule.getAuthority(), rule.getSourceDocument(), rule.getSourceUrl(),
                rule.getVerificationDate(), rule.getRulePayload());
        List<StatutoryTdsRule> verified = tdsRepository.findByFinancialYearAndReleaseStatus(
                rule.getFinancialYear(), RuleVersionSetStatus.VERIFIED);
        for (StatutoryTdsRule other : verified) {
            if (!other.getId().equals(rule.getId())
                    && overlaps(rule.getEffectiveFrom(), rule.getEffectiveTo(),
                    other.getEffectiveFrom(), other.getEffectiveTo())) {
                throw ApiException.conflict(ApiMessages.STATUTORY_RULE_PERIOD_OVERLAP);
            }
        }
        rule.setReleaseStatus(RuleVersionSetStatus.VERIFIED);
        rule.setVerifiedBy(actor.userId());
        rule.setVerifiedAt(LocalDateTime.now());
        tdsRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_VERIFIED, AuditActions.ENTITY_STATUTORY_TDS_RULE,
                rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleResponse supersedeTds(AuthenticatedUser actor, UUID id) {
        StatutoryTdsRule rule = tdsRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiMessages.STATUTORY_RULE_NOT_FOUND));
        requireVerifiedForSupersede(rule.getReleaseStatus());
        rule.setReleaseStatus(RuleVersionSetStatus.SUPERSEDED);
        tdsRepository.save(rule);
        audit(actor, AuditActions.STATUTORY_RULE_SUPERSEDED, AuditActions.ENTITY_STATUTORY_TDS_RULE,
                rule.getId());
        return toResponse(rule);
    }

    private void saveTds(StatutoryTdsRule rule) {
        try {
            tdsRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(ApiMessages.STATUTORY_RULE_ALREADY_EXISTS);
        }
    }

    // ---- reads ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StatutoryRuleResponse> listForVersionSet(UUID ruleVersionSetId) {
        requireVersionSet(ruleVersionSetId);
        java.util.ArrayList<StatutoryRuleResponse> all = new java.util.ArrayList<>();
        pfRepository.findByRuleVersionSetId(ruleVersionSetId)
                .ifPresent(r -> all.add(toResponse(r)));
        ptRepository.findByRuleVersionSetId(ruleVersionSetId)
                .forEach(r -> all.add(toResponse(r)));
        tdsRepository.findByRuleVersionSetId(ruleVersionSetId)
                .ifPresent(r -> all.add(toResponse(r)));
        return all;
    }

    // ---- shared validation ----------------------------------------------

    private void requireVersionSet(UUID id) {
        if (!versionSetRepository.existsById(id)) {
            throw ApiException.notFound(ApiMessages.STATUTORY_RULE_VERSION_SET_NOT_FOUND);
        }
    }

    private void requireEffectiveRange(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ApiMessages.STATUTORY_RULE_EFFECTIVE_RANGE_INVALID);
        }
    }

    private void requireDraft(RuleVersionSetStatus status) {
        if (status != RuleVersionSetStatus.DRAFT) {
            throw ApiException.conflict(ApiMessages.STATUTORY_RULE_NOT_DRAFT);
        }
    }

    private void requireDraftForVerify(RuleVersionSetStatus status) {
        if (status != RuleVersionSetStatus.DRAFT) {
            throw ApiException.conflict(ApiMessages.STATUTORY_RULE_NOT_VERIFIABLE_STATE);
        }
    }

    private void requireVerifiedForSupersede(RuleVersionSetStatus status) {
        if (status != RuleVersionSetStatus.VERIFIED) {
            throw ApiException.conflict(ApiMessages.STATUTORY_RULE_NOT_SUPERSEDABLE_STATE);
        }
    }

    /**
     * Verification completeness gate: complete source metadata AND a non-empty,
     * valid-JSON payload. A rule cannot be VERIFIED with missing required data or
     * an unparseable/empty payload. This does not assert the payload contains
     * legally-correct values (that is the release authority's responsibility), but
     * it prevents marking structurally-incomplete data VERIFIED.
     */
    private void requireCompleteForVerify(String authority, String sourceDocument,
                                          String sourceUrl, LocalDate verificationDate,
                                          String payload) {
        boolean metadataComplete = isPresent(authority) && isPresent(sourceDocument)
                && isPresent(sourceUrl) && verificationDate != null;
        boolean payloadComplete = isPresent(payload) && isJsonObject(payload);
        if (!metadataComplete || !payloadComplete) {
            throw ApiException.badRequest(ApiMessages.STATUTORY_RULE_VERIFY_INCOMPLETE);
        }
    }

    private boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }

    /** Validate a supplied payload is parseable JSON; returns it unchanged (or null). */
    private String validatedPayloadOrNull(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        if (!isValidJson(payload)) {
            throw ApiException.badRequest(ApiMessages.STATUTORY_RULE_VERIFY_INCOMPLETE);
        }
        return payload;
    }

    private boolean isValidJson(String s) {
        try {
            objectMapper.readTree(s);
            return true;
        } catch (JacksonException ex) {
            return false;
        }
    }

    private boolean isJsonObject(String s) {
        try {
            return objectMapper.readTree(s).isObject();
        } catch (JacksonException ex) {
            return false;
        }
    }

    /** Half-open effective-range overlap: null effective_to means open-ended. */
    private boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        LocalDate aEnd = aTo == null ? LocalDate.MAX : aTo;
        LocalDate bEnd = bTo == null ? LocalDate.MAX : bTo;
        return !aFrom.isAfter(bEnd) && !bFrom.isAfter(aEnd);
    }

    private void audit(AuthenticatedUser actor, String action, String entity, UUID entityId) {
        auditService.recordInTransaction(new AuditEvent(actor.userId(), actor.companyId(),
                actor.scopeType(), action, entity, entityId, "SUCCESS", null, null));
    }

    private StatutoryRuleResponse toResponse(StatutoryPfRule r) {
        return new StatutoryRuleResponse(r.getId(), r.getRuleVersionSetId(),
                r.getRuleType().name(), r.getJurisdiction(), null, null, null, null, null,
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getAuthority(), r.getSourceDocument(),
                r.getSourceUrl(), r.getVerificationDate(), r.getReleaseStatus().name(),
                r.getRulePayload(), r.getCreatedBy(), r.getCreatedAt(), r.getVerifiedBy(),
                r.getVerifiedAt());
    }

    private StatutoryRuleResponse toResponse(StatutoryPtRule r) {
        return new StatutoryRuleResponse(r.getId(), r.getRuleVersionSetId(),
                r.getRuleType().name(), r.getJurisdiction(), r.getPtState().name(),
                r.getLocalBody(), r.getPeriodicity().name(), null, null,
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getAuthority(), r.getSourceDocument(),
                r.getSourceUrl(), r.getVerificationDate(), r.getReleaseStatus().name(),
                r.getRulePayload(), r.getCreatedBy(), r.getCreatedAt(), r.getVerifiedBy(),
                r.getVerifiedAt());
    }

    private StatutoryRuleResponse toResponse(StatutoryTdsRule r) {
        return new StatutoryRuleResponse(r.getId(), r.getRuleVersionSetId(),
                r.getRuleType().name(), r.getJurisdiction(), null, null, null,
                r.getFinancialYear(), r.getTaxRegime(),
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getAuthority(), r.getSourceDocument(),
                r.getSourceUrl(), r.getVerificationDate(), r.getReleaseStatus().name(),
                r.getRulePayload(), r.getCreatedBy(), r.getCreatedAt(), r.getVerifiedBy(),
                r.getVerifiedAt());
    }
}
