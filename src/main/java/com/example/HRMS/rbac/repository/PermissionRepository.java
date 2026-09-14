package com.example.HRMS.rbac.repository;

import com.example.HRMS.rbac.entity.Permission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link Permission}. */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
}
