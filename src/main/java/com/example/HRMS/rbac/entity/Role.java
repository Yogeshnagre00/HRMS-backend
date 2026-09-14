package com.example.HRMS.rbac.entity;

import com.example.HRMS.common.security.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A named role (table {@code role}).
 *
 * <p>{@code code} is the stable identifier (e.g. {@code SUPER_ADMIN}).
 * {@code assignable} is {@code false} for roles reserved for future modules
 * (EMPLOYEE for ESS, MANAGER for MSS) so v0 cannot assign them.
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "assignable", nullable = false)
    private boolean assignable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Role() {
        // JPA + programmatic construction by the RBAC service
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public boolean isAssignable() {
        return assignable;
    }

    public void setAssignable(boolean assignable) {
        this.assignable = assignable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
