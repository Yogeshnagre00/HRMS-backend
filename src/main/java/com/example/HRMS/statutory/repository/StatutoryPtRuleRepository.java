package com.example.HRMS.statutory.repository;

import com.example.HRMS.statutory.entity.PtRuleState;
import com.example.HRMS.statutory.entity.RuleVersionSetStatus;
import com.example.HRMS.statutory.entity.StatutoryPtRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link StatutoryPtRule}. Persistence only — the
 * verification gate, immutability and effective-date overlap invariant (per
 * state) are enforced in the service layer.
 */
public interface StatutoryPtRuleRepository extends JpaRepository<StatutoryPtRule, UUID> {

    List<StatutoryPtRule> findByRuleVersionSetId(UUID ruleVersionSetId);

    /** VERIFIED PT rules for a state, for effective-date overlap checks. */
    List<StatutoryPtRule> findByPtStateAndReleaseStatus(PtRuleState ptState,
                                                        RuleVersionSetStatus releaseStatus);
}
