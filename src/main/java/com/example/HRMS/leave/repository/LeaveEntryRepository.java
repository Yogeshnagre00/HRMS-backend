package com.example.HRMS.leave.repository;

import com.example.HRMS.leave.entity.LeaveEntry;
import com.example.HRMS.leave.entity.LeaveEntryStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link LeaveEntry}. Persistence only — no business
 * logic. Scope isolation is enforced in the service via employee resolution;
 * the one-active-entry-per-employee-per-date invariant and paid-leave balance
 * consumption are handled in the service.
 */
public interface LeaveEntryRepository extends JpaRepository<LeaveEntry, UUID> {

    Page<LeaveEntry> findByEmployeeId(UUID employeeId, Pageable pageable);

    Optional<LeaveEntry> findByEmployeeIdAndLeaveDateAndStatus(
            UUID employeeId, LocalDate leaveDate, LeaveEntryStatus status);

    /** All entries for an employee in a given status (read-only, for payroll calc). */
    java.util.List<LeaveEntry> findByEmployeeIdAndStatus(UUID employeeId, LeaveEntryStatus status);
}
