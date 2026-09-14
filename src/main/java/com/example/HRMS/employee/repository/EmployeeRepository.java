package com.example.HRMS.employee.repository;

import com.example.HRMS.employee.entity.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link Employee}. Persistence only — no business logic.
 *
 * <p>Reads are always scoped by {@code legal_entity_id} so company isolation is
 * enforced at the query level (a caller can never load another entity's
 * employee by surrogate id).
 */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Page<Employee> findByLegalEntityId(UUID legalEntityId, Pageable pageable);

    Optional<Employee> findByIdAndLegalEntityId(UUID id, UUID legalEntityId);

    boolean existsByLegalEntityIdAndEmployeeId(UUID legalEntityId, String employeeId);
}
