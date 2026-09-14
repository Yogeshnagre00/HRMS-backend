package com.example.HRMS.statutory.service;

import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.statutory.dto.StatutoryDtos.RuleVersionSetResponse;
import com.example.HRMS.statutory.entity.StatutoryRuleVersionSet;
import com.example.HRMS.statutory.repository.StatutoryRuleVersionSetRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to verified statutory rule version sets (API spec §8).
 *
 * <p>Rule version sets are verified release inputs; v0 exposes them read-only so
 * statutory configuration and (later) payroll can reference a specific version.
 * This service does not create, verify or mutate sets, and it never fabricates
 * statutory values (AGENTS §11).
 */
@Service
public class RuleVersionSetService {

    private final StatutoryRuleVersionSetRepository repository;

    public RuleVersionSetService(StatutoryRuleVersionSetRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RuleVersionSetResponse> list() {
        return repository.findAllByOrderByEffectiveFromDescIdAsc().stream()
                .map(RuleVersionSetService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RuleVersionSetResponse getById(UUID id) {
        return repository.findById(id)
                .map(RuleVersionSetService::toResponse)
                .orElseThrow(() -> ApiException.notFound(
                        ApiMessages.STATUTORY_RULE_VERSION_SET_NOT_FOUND));
    }

    private static RuleVersionSetResponse toResponse(StatutoryRuleVersionSet s) {
        return new RuleVersionSetResponse(s.getId(), s.getJurisdiction(), s.getPfRuleVersion(),
                s.getPtRuleVersion(), s.getTdsRuleVersion(), s.getEffectiveFrom(),
                s.getEffectiveTo(), s.getSourceReference(), s.getVerifiedAt(),
                s.getStatus().name());
    }
}
