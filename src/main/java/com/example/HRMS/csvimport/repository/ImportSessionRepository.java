package com.example.HRMS.csvimport.repository;

import com.example.HRMS.csvimport.entity.ImportSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link ImportSession}. Persistence only. Reads are
 * scoped by company so retrieval enforces isolation (an import id from another
 * company cannot be loaded, and its existence is not disclosed).
 */
public interface ImportSessionRepository extends JpaRepository<ImportSession, UUID> {

    Optional<ImportSession> findByIdAndCompanyId(UUID id, UUID companyId);
}
