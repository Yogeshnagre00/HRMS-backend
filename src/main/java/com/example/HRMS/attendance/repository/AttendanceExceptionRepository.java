package com.example.HRMS.attendance.repository;

import com.example.HRMS.attendance.entity.AttendanceException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link AttendanceException}. Persistence only — no
 * business logic. Scope isolation is enforced in the service via employee
 * resolution; the one-per-employee-per-date invariant is checked in the service.
 */
public interface AttendanceExceptionRepository
        extends JpaRepository<AttendanceException, UUID> {

    Page<AttendanceException> findByEmployeeId(UUID employeeId, Pageable pageable);

    Optional<AttendanceException> findByEmployeeIdAndAttendanceDate(UUID employeeId,
                                                                    LocalDate attendanceDate);

    /** All exceptions for an employee (read-only, for payroll calc). */
    java.util.List<AttendanceException> findByEmployeeId(UUID employeeId);
}
