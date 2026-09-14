package com.example.HRMS.rbac.repository;

import com.example.HRMS.rbac.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link UserRole} assignments. */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
