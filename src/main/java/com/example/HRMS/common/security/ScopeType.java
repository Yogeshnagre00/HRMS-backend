package com.example.HRMS.common.security;

/**
 * Authorization scope of a user or role — a shared authorization value type used
 * across the auth, rbac, security and audit modules.
 *
 * <p>{@code PLATFORM} is the platform-wide scope (SUPER_ADMIN). {@code COMPANY}
 * is bound to a single company. Legal-entity scope can be added later without
 * changing this enum's existing values. It lives in {@code common} because it is
 * a dependency-free vocabulary shared by multiple modules, which keeps the
 * feature modules free of cross-dependencies just to share this enum.
 */
public enum ScopeType {
    PLATFORM,
    COMPANY
}
