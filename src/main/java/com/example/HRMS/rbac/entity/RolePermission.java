package com.example.HRMS.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Mapping of a role to a permission (table {@code role_permission}). */
@Entity
@Table(name = "role_permission")
public class RolePermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    protected RolePermission() {
        // JPA
    }

    public RolePermission(UUID id, UUID roleId, UUID permissionId) {
        this.id = id;
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }
}
