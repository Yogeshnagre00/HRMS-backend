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

    // Company + Legal Entity (V0-004)
    public static final String COMPANY_CREATED = "COMPANY_CREATED";
    public static final String COMPANY_UPDATED = "COMPANY_UPDATED";
    public static final String LEGAL_ENTITY_CREATED = "LEGAL_ENTITY_CREATED";
    public static final String LEGAL_ENTITY_UPDATED = "LEGAL_ENTITY_UPDATED";

    // Statutory configuration (V0-005)
    public static final String STATUTORY_CONFIG_CREATED = "STATUTORY_CONFIG_CREATED";
    public static final String STATUTORY_CONFIG_UPDATED = "STATUTORY_CONFIG_UPDATED";

    // Employee master (V2-001)
    public static final String EMPLOYEE_CREATED = "EMPLOYEE_CREATED";
    public static final String EMPLOYEE_UPDATED = "EMPLOYEE_UPDATED";

    // Employee bank account (V2-002)
    public static final String BANK_ACCOUNT_CREATED = "BANK_ACCOUNT_CREATED";
    public static final String BANK_ACCOUNT_UPDATED = "BANK_ACCOUNT_UPDATED";

    // Employee opening tax state (V2-003)
    public static final String OPENING_TAX_STATE_CREATED = "OPENING_TAX_STATE_CREATED";
    public static final String OPENING_TAX_STATE_UPDATED = "OPENING_TAX_STATE_UPDATED";

    public static final String ENTITY_USER = "app_user";
    public static final String ENTITY_COMPANY = "company";
    public static final String ENTITY_LEGAL_ENTITY = "legal_entity";
    public static final String ENTITY_STATUTORY_CONFIGURATION = "statutory_configuration";
    public static final String ENTITY_EMPLOYEE = "employee";
    public static final String ENTITY_EMPLOYEE_BANK_ACCOUNT = "employee_bank_account";
    public static final String ENTITY_EMPLOYEE_OPENING_TAX_STATE = "employee_opening_tax_state";
}
