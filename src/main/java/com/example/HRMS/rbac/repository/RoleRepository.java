package com.example.HRMS.rbac.repository;

import com.example.HRMS.rbac.entity.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link Role}. */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
