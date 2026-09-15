package com.example.HRMS.payroll.repository;

import com.example.HRMS.payroll.entity.PayrollRun;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link PayrollRun}. Persistence only — no business
 * logic.
 *
 * <p>Reads are scoped by {@code legal_entity_id} so company isolation is
 * enforced at the query level (a run from another company cannot be loaded by
 * surrogate id and its existence is not disclosed). The primary-run uniqueness
 * check queries for an existing primary (parent-less) run for a legal entity and
 * payroll month; the conditional invariant itself is enforced in the service.
 */
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Page<PayrollRun> findByLegalEntityId(UUID legalEntityId, Pageable pageable);

    Optional<PayrollRun> findByIdAndLegalEntityId(UUID id, UUID legalEntityId);

    boolean existsByLegalEntityIdAndPayrollMonthAndParentPayrollRunIdIsNull(
            UUID legalEntityId, LocalDate payrollMonth);

    /**
     * Pessimistic-write lock on the run row, used by calculate/recalculate
     * (V2-008A) to serialize concurrent calculation and re-check status under
     * the lock (API §16.2). Portable to H2 and PostgreSQL (SELECT ... FOR UPDATE).
     */
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT r FROM PayrollRun r WHERE r.id = :id AND r.legalEntityId = :legalEntityId")
    Optional<PayrollRun> findByIdAndLegalEntityIdForUpdate(UUID id, UUID legalEntityId);
}
