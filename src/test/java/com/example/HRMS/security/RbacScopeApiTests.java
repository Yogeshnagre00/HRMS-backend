package com.example.HRMS.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.HRMS.auth.entity.AppUser;
import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.auth.repository.AppUserRepository;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.rbac.entity.UserRole;
import com.example.HRMS.rbac.repository.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * RBAC + SCOPE acceptance tests (SEC-001, SEC-002, SEC-007, SEC-008 and the
 * privilege-escalation scenarios). Uses the seeded roles/permissions and real
 * users provisioned per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacScopeApiTests {

    // Seeded role ids from V4.
    private static final UUID ROLE_SUPER_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ROLE_COMPANY_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID ROLE_PAYROLL_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000103");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID companyA;
    private UUID companyB;
    private UUID companyAAdminId;
    private UUID companyBUserId;
    private UUID payrollAdminId;

    @BeforeEach
    void setUp() {
        // Clear rows that reference app_user / company (incl. those left by other
        // test classes on the shared in-memory database) in FK-safe order.
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM revoked_token");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM statutory_configuration");
        jdbcTemplate.update("DELETE FROM legal_entity");
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM company");

        companyA = insertCompany("Company A");
        companyB = insertCompany("Company B");

        UUID superId = createUser("superadmin", ScopeType.PLATFORM, null);
        assign(superId, ROLE_SUPER_ADMIN);

        companyAAdminId = createUser("compAadmin", ScopeType.COMPANY, companyA);
        assign(companyAAdminId, ROLE_COMPANY_ADMIN);

        companyBUserId = createUser("compBuser", ScopeType.COMPANY, companyB);
        assign(companyBUserId, ROLE_COMPANY_ADMIN);

        payrollAdminId = createUser("payadmin", ScopeType.COMPANY, companyA);
        assign(payrollAdminId, ROLE_PAYROLL_ADMIN);
    }

    private UUID insertCompany(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO company (id, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id.toString(), name);
        return id;
    }

    private UUID createUser(String username, ScopeType scope, UUID companyId) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Str0ngPass!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setScopeType(scope);
        user.setCompanyId(companyId);
        user.setMfaEnabled(false);
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user).getId();
    }

    private void assign(UUID userId, UUID roleId) {
        userRoleRepository.save(new UserRole(UUID.randomUUID(), userId, roleId));
    }

    private String login(String username) throws Exception {
        String json = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"Str0ngPass!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asString();
    }

    private String bearer(String username) throws Exception {
        return "Bearer " + login(username);
    }

    // --- SEC-001 ---
    @Test
    void unauthenticatedRequestToProtectedApiIs401() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    // --- SEC-002 / missing permission ---
    @Test
    void authenticatedUserWithoutRequiredPermissionIs403() throws Exception {
        // PAYROLL_ADMIN has neither role.read nor user.read.
        mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer("payadmin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void superAdminCanListRoles() throws Exception {
        mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer("superadmin")))
                .andExpect(status().isOk());
    }

    @Test
    void companyAdminHasRoleReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer("compAadmin")))
                .andExpect(status().isOk());
    }

    // --- SEC-008 authorization/me ---
    @Test
    void authorizationMeReturnsEffectivePermissionsAndScope() throws Exception {
        mockMvc.perform(get("/api/v1/authorization/me").header("Authorization", bearer("payadmin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("COMPANY"))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    // --- SEC-007 cross-company scope ---
    @Test
    void companyUserCanReadUserInOwnCompany() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + payrollAdminId + "/roles")
                        .header("Authorization", bearer("compAadmin")))
                .andExpect(status().isOk());
    }

    @Test
    void companyUserCannotReadUserInAnotherCompany() throws Exception {
        // companyA admin tries to read a companyB user -> not found (no disclosure).
        mockMvc.perform(get("/api/v1/users/" + companyBUserId + "/roles")
                        .header("Authorization", bearer("compAadmin")))
                .andExpect(status().isNotFound());
    }

    // --- privilege escalation: company user cannot grant SUPER_ADMIN ---
    @Test
    void companyAdminCannotAssignSuperAdminRole() throws Exception {
        String body = "{\"roleIds\":[\"" + ROLE_SUPER_ADMIN + "\"]}";
        mockMvc.perform(put("/api/v1/users/" + payrollAdminId + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    // --- client cannot choose another company scope (target out of scope) ---
    @Test
    void companyAdminCannotAssignRolesToUserInAnotherCompany() throws Exception {
        String body = "{\"roleIds\":[\"" + ROLE_PAYROLL_ADMIN + "\"]}";
        mockMvc.perform(put("/api/v1/users/" + companyBUserId + "/roles")
                        .header("Authorization", bearer("compAadmin"))
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    // --- platform admin can assign a company role within a company ---
    @Test
    void superAdminCanAssignAssignableRole() throws Exception {
        String body = "{\"roleIds\":[\"" + ROLE_PAYROLL_ADMIN + "\"]}";
        mockMvc.perform(put("/api/v1/users/" + companyBUserId + "/roles")
                        .header("Authorization", bearer("superadmin"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes[0]").value("PAYROLL_ADMIN"));
    }

    // --- reserved role cannot be assigned ---
    @Test
    void reservedEmployeeRoleCannotBeAssigned() throws Exception {
        UUID employeeRole = UUID.fromString("00000000-0000-0000-0000-000000000104");
        String body = "{\"roleIds\":[\"" + employeeRole + "\"]}";
        mockMvc.perform(put("/api/v1/users/" + companyBUserId + "/roles")
                        .header("Authorization", bearer("superadmin"))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
}
