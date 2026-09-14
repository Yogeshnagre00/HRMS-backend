package com.example.HRMS.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Assignment of a role to a user (table {@code user_role}).
 *
 * <p>The RBAC module references a user by id only; it does not own the user
 * entity (owned by the {@code auth} module).
 */
@Entity
@Table(name = "user_role")
public class UserRole {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    protected UserRole() {
        // JPA
    }

    public UserRole(UUID id, UUID userId, UUID roleId) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }
}
