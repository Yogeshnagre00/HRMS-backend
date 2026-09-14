package com.example.HRMS.audit.service;

/** Stable audit action codes for security-sensitive events. */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "AUTH_LOGIN_FAILURE";
    public static final String LOGOUT = "AUTH_LOGOUT";
    public static final String PASSWORD_CHANGE = "AUTH_PASSWORD_CHANGE";
    public static final String MFA_SUCCESS = "AUTH_MFA_SUCCESS";
    public static final String MFA_FAILURE = "AUTH_MFA_FAILURE";
    public static final String ROLE_ASSIGNMENT_CHANGE = "RBAC_USER_ROLES_CHANGED";
    public static final String USER_ACTIVATION_CHANGE = "USER_ACTIVATION_CHANGED";
    public static final String USER_CREATED = "USER_CREATED";

    public static final String ENTITY_USER = "app_user";
}
