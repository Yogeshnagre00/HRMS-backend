package com.example.HRMS.auth.repository;

import com.example.HRMS.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence access for {@link RefreshToken}. Persistence only — no business logic. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /** Revoke all currently-active refresh tokens for a user (e.g. on password change). */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now "
            + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
