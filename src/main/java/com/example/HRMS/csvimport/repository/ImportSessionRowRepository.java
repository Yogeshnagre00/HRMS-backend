package com.example.HRMS.csvimport.repository;

import com.example.HRMS.csvimport.entity.ImportSessionRow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link ImportSessionRow}. Persistence only. Rows are
 * returned ordered by physical row number for deterministic retrieval.
 */
public interface ImportSessionRowRepository extends JpaRepository<ImportSessionRow, UUID> {

    List<ImportSessionRow> findByImportSessionIdOrderByRowNumberAsc(UUID importSessionId);
}
