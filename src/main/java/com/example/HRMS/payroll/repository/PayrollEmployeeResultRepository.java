package com.example.HRMS.payroll.repository;

import com.example.HRMS.payroll.entity.PayrollEmployeeResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link PayrollEmployeeResult}. Persistence only. Unique
 * per (payroll_run, employee); recalculation deletes the run's results before
 * re-inserting within one transaction.
 */
public interface PayrollEmployeeResultRepository
        extends JpaRepository<PayrollEmployeeResult, UUID> {

    List<PayrollEmployeeResult> findByPayrollRunId(UUID payrollRunId);

    /**
     * Employee results for a run, deterministically ordered by the business
     * Employee ID ascending then result id ascending (API §16.1). Ordering by
     * the business id (not the surrogate FK) requires a join to Employee.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM PayrollEmployeeResult r, Employee e
            WHERE r.payrollRunId = :payrollRunId AND e.id = r.employeeId
            ORDER BY e.employeeId ASC, r.id ASC
            """)
    Page<PayrollEmployeeResult> findByPayrollRunIdOrderedByBusinessId(UUID payrollRunId,
                                                                      Pageable pageable);

    Optional<PayrollEmployeeResult> findByPayrollRunIdAndEmployeeId(UUID payrollRunId,
                                                                    UUID employeeId);
}
