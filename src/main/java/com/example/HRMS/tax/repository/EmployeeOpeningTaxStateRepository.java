package com.example.HRMS.tax.repository;

import com.example.HRMS.tax.entity.EmployeeOpeningTaxState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link EmployeeOpeningTaxState}. Persistence only.
 * The unique (employee_id, financial_year) DB constraint is authoritative for
 * the "one opening state per employee per FY" invariant.
 */
public interface EmployeeOpeningTaxStateRepository
        extends JpaRepository<EmployeeOpeningTaxState, UUID> {

    Optional<EmployeeOpeningTaxState> findByEmployeeIdAndFinancialYear(
            UUID employeeId, String financialYear);
}
