package com.example.HRMS.payrollinput.repository;

import com.example.HRMS.payrollinput.entity.VariableEarning;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link VariableEarning}. Persistence only — no business
 * logic. Scope isolation is enforced in the service via employee resolution.
 */
public interface VariableEarningRepository extends JpaRepository<VariableEarning, UUID> {

    Page<VariableEarning> findByEmployeeId(UUID employeeId, Pageable pageable);

    /** All variable earnings for an employee in a payroll run (read-only, for calc). */
    java.util.List<VariableEarning> findByEmployeeIdAndPayrollRunId(UUID employeeId,
                                                                    UUID payrollRunId);
}
