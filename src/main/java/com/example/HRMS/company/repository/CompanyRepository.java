package com.example.HRMS.company.repository;

import com.example.HRMS.company.entity.Company;
import com.example.HRMS.company.entity.CompanyStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link Company}. Persistence only — no business logic. */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByStatus(CompanyStatus status);

    Optional<Company> findFirstByStatus(CompanyStatus status);
}
