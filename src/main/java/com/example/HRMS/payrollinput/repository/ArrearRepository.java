package com.example.HRMS.payrollinput.repository;

import com.example.HRMS.payrollinput.entity.Arrear;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link Arrear}. Persistence only — no business logic.
 * Scope isolation is enforced in the service via employee resolution.
 */
public interface ArrearRepository extends JpaRepository<Arrear, UUID> {

    Page<Arrear> findByEmployeeId(UUID employeeId, Pageable pageable);

    /** All arrears for an employee in a payroll run (read-only, for calc). */
    java.util.List<Arrear> findByEmployeeIdAndPayrollRunId(UUID employeeId, UUID payrollRunId);
}
