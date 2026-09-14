package com.example.HRMS.rbac.repository;

import com.example.HRMS.rbac.entity.RolePermission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link RolePermission} mappings. */
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

    List<RolePermission> findByRoleIdIn(List<UUID> roleIds);

    void deleteByRoleId(UUID roleId);
}
