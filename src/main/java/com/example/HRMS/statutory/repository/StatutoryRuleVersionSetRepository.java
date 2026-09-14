package com.example.HRMS.statutory.repository;

import com.example.HRMS.statutory.entity.StatutoryRuleVersionSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link StatutoryRuleVersionSet}. Persistence only — no
 * business logic. Rule version sets are read-only verified release inputs in v0.
 */
public interface StatutoryRuleVersionSetRepository
        extends JpaRepository<StatutoryRuleVersionSet, UUID> {

    /** Deterministic listing: newest effective first, then by id for stable ties. */
    List<StatutoryRuleVersionSet> findAllByOrderByEffectiveFromDescIdAsc();
}
