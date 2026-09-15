package com.example.HRMS.workcalendar.repository;

import com.example.HRMS.workcalendar.entity.WorkCalendar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link WorkCalendar}. Persistence only — no business
 * logic. Reads are scoped by {@code legal_entity_id} so company isolation is
 * enforced at the query level. Overlap detection loads the legal entity's
 * calendars and is evaluated in the service (portable to H2; no partial index).
 */
public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, UUID> {

    Page<WorkCalendar> findByLegalEntityId(UUID legalEntityId, Pageable pageable);

    Optional<WorkCalendar> findByIdAndLegalEntityId(UUID id, UUID legalEntityId);

    List<WorkCalendar> findByLegalEntityId(UUID legalEntityId);
}
