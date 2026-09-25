package com.example.HRMS.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.HRMS.rbac.entity.Role;
import com.example.HRMS.rbac.repository.PermissionRepository;
import com.example.HRMS.rbac.repository.RolePermissionRepository;
import com.example.HRMS.rbac.repository.RoleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * DATABASE tests for V0-003: the V3 schema is applied, and the V4 seed produced
 * the expected roles, permissions and least-privilege role-permission mappings.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRbacSchemaTests {

    private static final List<String> EXPECTED_TABLES = List.of(
            "app_user", "role", "permission", "role_permission", "user_role",
            "revoked_token", "audit_log");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Test
    void allAuthRbacTablesExist() {
        for (String table : EXPECTED_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE UPPER(table_name) = UPPER(?)", Integer.class, table);
            assertThat(count).as("table %s should exist", table).isNotNull().isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void v0RolesAreSeeded() {
        assertThat(roleRepository.findByCode("SUPER_ADMIN")).isPresent();
        assertThat(roleRepository.findByCode("COMPANY_ADMIN")).isPresent();
        assertThat(roleRepository.findByCode("PAYROLL_ADMIN")).isPresent();
    }

    @Test
    void reservedRolesExistButAreNotAssignable() {
        Role employee = roleRepository.findByCode("EMPLOYEE").orElseThrow();
        Role manager = roleRepository.findByCode("MANAGER").orElseThrow();
        assertThat(employee.isAssignable()).isFalse();
        assertThat(manager.isAssignable()).isFalse();
    }

    @Test
    void superAdminIsPlatformScopedAndCompanyRolesAreCompanyScoped() {
        assertThat(roleRepository.findByCode("SUPER_ADMIN").orElseThrow().getScopeType().name())
                .isEqualTo("PLATFORM");
        assertThat(roleRepository.findByCode("COMPANY_ADMIN").orElseThrow().getScopeType().name())
                .isEqualTo("COMPANY");
        assertThat(roleRepository.findByCode("PAYROLL_ADMIN").orElseThrow().getScopeType().name())
                .isEqualTo("COMPANY");
    }

    @Test
    void permissionCatalogueIsSeeded() {
        // 9 V4-seeded permissions + statutory.release added in V19 (Phase 2).
        assertThat(permissionRepository.count()).isEqualTo(10);
    }

    @Test
    void leastPrivilegeMappingsAreApplied() {
        // Reserved roles have no permissions.
        var employee = roleRepository.findByCode("EMPLOYEE").orElseThrow();
        var manager = roleRepository.findByCode("MANAGER").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(employee.getId())).isEmpty();
        assertThat(rolePermissionRepository.findByRoleId(manager.getId())).isEmpty();

        // PAYROLL_ADMIN has payroll.admin + audit.read only (2 mappings), not user/role admin.
        var payroll = roleRepository.findByCode("PAYROLL_ADMIN").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(payroll.getId())).hasSize(2);

        // SUPER_ADMIN has more permissions than PAYROLL_ADMIN (least privilege differentiation).
        var superAdmin = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        assertThat(rolePermissionRepository.findByRoleId(superAdmin.getId()).size())
                .isGreaterThan(rolePermissionRepository.findByRoleId(payroll.getId()).size());
    }
}
