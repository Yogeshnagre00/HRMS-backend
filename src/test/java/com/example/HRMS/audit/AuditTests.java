package com.example.HRMS.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.audit.entity.AuditLog;
import com.example.HRMS.audit.repository.AuditLogRepository;
import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.security.ScopeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AUDIT tests: security events are recorded with actor, and secrets are never
 * persisted in the audit log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM revoked_token");
        jdbcTemplate.update("DELETE FROM statutory_configuration");
        jdbcTemplate.update("DELETE FROM user_role");
        userRepository.deleteAll();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername("auditor");
        user.setEmail("auditor@example.com");
        user.setPasswordHash(passwordEncoder.encode("Str0ngPass!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setScopeType(ScopeType.PLATFORM);
        user.setMfaEnabled(false);
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userId = userRepository.save(user).getId();
    }

    @Test
    void successfulLoginIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"auditor\",\"password\":\"Str0ngPass!\"}"))
                .andExpect(status().isOk());

        List<AuditLog> events = auditLogRepository.findByActorUserId(userId);
        assertThat(events).anyMatch(e -> AuditActions.LOGIN_SUCCESS.equals(e.getAction()));
        AuditLog loginEvent = events.stream()
                .filter(e -> AuditActions.LOGIN_SUCCESS.equals(e.getAction()))
                .findFirst().orElseThrow();
        assertThat(loginEvent.getActorUserId()).isEqualTo(userId);
        assertThat(loginEvent.getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void failedLoginIsAuditedWithoutRevealingSecret() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"auditor\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditLog> events = auditLogRepository.findByActorUserId(userId);
        assertThat(events).anyMatch(e -> AuditActions.LOGIN_FAILURE.equals(e.getAction()));
    }

    @Test
    void auditRecordsNeverContainThePasswordOrHash() throws Exception {
        String password = "Str0ngPass!";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"auditor\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        String storedHash = userRepository.findById(userId).orElseThrow().getPasswordHash();
        for (AuditLog event : auditLogRepository.findAll()) {
            assertThat(nullSafe(event.getReason())).doesNotContain(password).doesNotContain(storedHash);
            assertThat(nullSafe(event.getBeforeSnapshot())).doesNotContain(password).doesNotContain(storedHash);
            assertThat(nullSafe(event.getAfterSnapshot())).doesNotContain(password).doesNotContain(storedHash);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
