package com.example.HRMS.security.token;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link RevokedToken} (JWT denylist). */
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    boolean existsByJti(String jti);
}
