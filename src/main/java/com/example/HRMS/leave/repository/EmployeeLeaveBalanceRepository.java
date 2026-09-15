package com.example.HRMS.leave.repository;

import com.example.HRMS.leave.entity.EmployeeLeaveBalance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence access for {@link EmployeeLeaveBalance}. Persistence only. The
 * unique (employee_id, financial_year) DB constraint is authoritative for the
 * "one balance per employee per FY" invariant.
 */
public interface EmployeeLeaveBalanceRepository
        extends JpaRepository<EmployeeLeaveBalance, UUID> {

    Optional<EmployeeLeaveBalance> findByEmployeeIdAndFinancialYear(
            UUID employeeId, String financialYear);

    /**
     * Pessimistic-write lock on the balance row for an employee + FY, used by the
     * LeaveEntry layer (V2-008A.5) to serialize concurrent paid-leave
     * create/cancel so {@code used_quantity} stays consistent and
     * {@code available_balance} never goes negative (V2-008A.2 concurrency
     * decision). Portable to H2 and PostgreSQL (SELECT ... FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM EmployeeLeaveBalance b "
            + "WHERE b.employeeId = :employeeId AND b.financialYear = :financialYear")
    Optional<EmployeeLeaveBalance> findByEmployeeIdAndFinancialYearForUpdate(
            UUID employeeId, String financialYear);
}
