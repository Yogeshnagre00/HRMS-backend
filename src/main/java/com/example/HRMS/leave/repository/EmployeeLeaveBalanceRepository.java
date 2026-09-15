package com.example.HRMS.leave.repository;

import com.example.HRMS.leave.entity.EmployeeLeaveBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link EmployeeLeaveBalance}. Persistence only. The
 * unique (employee_id, financial_year) DB constraint is authoritative for the
 * "one balance per employee per FY" invariant.
 */
public interface EmployeeLeaveBalanceRepository
        extends JpaRepository<EmployeeLeaveBalance, UUID> {

    Optional<EmployeeLeaveBalance> findByEmployeeIdAndFinancialYear(
            UUID employeeId, String financialYear);
}
