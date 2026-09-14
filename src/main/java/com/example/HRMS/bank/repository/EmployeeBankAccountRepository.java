package com.example.HRMS.bank.repository;

import com.example.HRMS.bank.entity.EmployeeBankAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link EmployeeBankAccount}. Persistence only — no
 * business logic (the "single current open account per employee" invariant is
 * enforced in the service layer per AGENTS §8).
 */
public interface EmployeeBankAccountRepository extends JpaRepository<EmployeeBankAccount, UUID> {

    /** The current (open-ended) bank account for an employee, if any. */
    Optional<EmployeeBankAccount> findFirstByEmployeeIdAndEffectiveToIsNull(UUID employeeId);
}
