package com.example.HRMS.statutory.repository;

import com.example.HRMS.statutory.entity.RuleVersionSetStatus;
import com.example.HRMS.statutory.entity.StatutoryPfRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link StatutoryPfRule}. Persistence only — the
 * verification gate, immutability and effective-date overlap invariant are
 * enforced in the service layer (portable to H2; no partial index).
 */
public interface StatutoryPfRuleRepository extends JpaRepository<StatutoryPfRule, UUID> {

    Optional<StatutoryPfRule> findByRuleVersionSetId(UUID ruleVersionSetId);

    /** VERIFIED PF rules for a jurisdiction, for effective-date overlap checks. */
    List<StatutoryPfRule> findByJurisdictionAndReleaseStatus(String jurisdiction,
                                                             RuleVersionSetStatus releaseStatus);
}
