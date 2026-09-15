package com.example.HRMS.compensation.repository;

import com.example.HRMS.compensation.entity.CompensationRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link CompensationRecord}. Persistence only — the
 * effective-date/overlap invariants are enforced in the service layer (AGENTS
 * §8). No generic base abstractions.
 */
public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {

    /** All records for an employee, most recent effective_from first. */
    List<CompensationRecord> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    /** The current open-ended record for an employee (effective_to IS NULL), if any. */
    Optional<CompensationRecord> findByEmployeeIdAndEffectiveToIsNull(UUID employeeId);

    /** Whether the employee has any compensation record. */
    boolean existsByEmployeeId(UUID employeeId);
}
