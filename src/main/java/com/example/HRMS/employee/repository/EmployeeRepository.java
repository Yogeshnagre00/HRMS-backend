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

    /**
     * Count employees in a legal entity whose employment period intersects a
     * payroll month window {@code [monthStart, monthEnd]} (both inclusive):
     * joined on or before the month end AND (no exit OR exited on or after the
     * month start). Used by the payroll run population count (V2-007). Joining
     * and exit dates are inclusive boundaries.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.legalEntityId = :legalEntityId
              AND e.joiningDate <= :monthEnd
              AND (e.exitDate IS NULL OR e.exitDate >= :monthStart)
            """)
    long countEligibleForPayrollMonth(java.util.UUID legalEntityId,
                                      java.time.LocalDate monthStart,
                                      java.time.LocalDate monthEnd);

    /**
     * All employees in a legal entity whose employment period intersects a
     * payroll month window {@code [monthStart, monthEnd]} (inclusive), ordered by
     * business Employee ID for deterministic payroll iteration. Used by the
     * payroll calculation (V2-008A) to build the employee population.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT e FROM Employee e
            WHERE e.legalEntityId = :legalEntityId
              AND e.joiningDate <= :monthEnd
              AND (e.exitDate IS NULL OR e.exitDate >= :monthStart)
            ORDER BY e.employeeId ASC, e.id ASC
            """)
    java.util.List<Employee> findEligibleForPayrollMonth(java.util.UUID legalEntityId,
                                                         java.time.LocalDate monthStart,
                                                         java.time.LocalDate monthEnd);
}
