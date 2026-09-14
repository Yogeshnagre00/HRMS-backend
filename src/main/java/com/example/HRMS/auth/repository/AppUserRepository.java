package com.example.HRMS.auth.repository;

import com.example.HRMS.auth.entity.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link AppUser}. Persistence only — no business logic. */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
