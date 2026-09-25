package com.example.HRMS.statutory.repository;

import com.example.HRMS.statutory.entity.RuleVersionSetStatus;
import com.example.HRMS.statutory.entity.StatutoryTdsRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link StatutoryTdsRule}. Persistence only — the
 * verification gate, immutability and effective-date overlap invariant (per
 * financial year) are enforced in the service layer.
 */
public interface StatutoryTdsRuleRepository extends JpaRepository<StatutoryTdsRule, UUID> {

    Optional<StatutoryTdsRule> findByRuleVersionSetId(UUID ruleVersionSetId);

    /** VERIFIED TDS rules for a financial year, for effective-date overlap checks. */
    List<StatutoryTdsRule> findByFinancialYearAndReleaseStatus(String financialYear,
                                                              RuleVersionSetStatus releaseStatus);
}
