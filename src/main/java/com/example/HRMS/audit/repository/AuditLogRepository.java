package com.example.HRMS.audit.repository;

import com.example.HRMS.audit.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link AuditLog}. Append-only from the application layer. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByCompanyId(UUID companyId, Pageable pageable);

    List<AuditLog> findByActorUserId(UUID actorUserId);
}
