package com.example.HRMS.statutory.repository;

import com.example.HRMS.statutory.entity.StatutoryConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link StatutoryConfiguration}. Persistence only — no
 * business logic (effective-window resolution and no-overlap invariants live in
 * the service layer per AGENTS §8).
 */
public interface StatutoryConfigurationRepository
        extends JpaRepository<StatutoryConfiguration, UUID> {

    /** All configuration rows for a legal entity, most recent effective_from first. */
    List<StatutoryConfiguration> findByLegalEntityIdOrderByEffectiveFromDesc(UUID legalEntityId);

    /** The single open-ended (current) configuration for a legal entity, if any. */
    Optional<StatutoryConfiguration> findFirstByLegalEntityIdAndEffectiveToIsNull(UUID legalEntityId);
}
