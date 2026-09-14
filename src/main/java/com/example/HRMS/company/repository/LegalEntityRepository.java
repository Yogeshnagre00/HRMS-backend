package com.example.HRMS.company.repository;

import com.example.HRMS.company.entity.CompanyStatus;
import com.example.HRMS.company.entity.LegalEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link LegalEntity}. Persistence only — no business logic. */
public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {

    List<LegalEntity> findByCompanyIdOrderByLegalNameAsc(UUID companyId);

    boolean existsByCompanyIdAndStatus(UUID companyId, CompanyStatus status);
}
