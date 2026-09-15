package com.example.HRMS.workcalendar.repository;

import com.example.HRMS.workcalendar.entity.WorkCalendarAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link WorkCalendarAssignment}. Persistence only — no
 * business logic. Assignment overlap is evaluated in the service over an
 * employee's existing assignments (portable to H2; no partial index).
 */
public interface WorkCalendarAssignmentRepository
        extends JpaRepository<WorkCalendarAssignment, UUID> {

    List<WorkCalendarAssignment> findByEmployeeIdOrderByEffectiveFromAsc(UUID employeeId);

    List<WorkCalendarAssignment> findByWorkCalendarIdOrderByEffectiveFromAsc(UUID workCalendarId);
}
