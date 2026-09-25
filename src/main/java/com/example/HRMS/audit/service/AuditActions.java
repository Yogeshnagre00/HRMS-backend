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

    // Employee leave balance (V2-004)
    public static final String LEAVE_BALANCE_CREATED = "LEAVE_BALANCE_CREATED";
    public static final String LEAVE_BALANCE_UPDATED = "LEAVE_BALANCE_UPDATED";

    // Employee CSV validation (V2-005)
    public static final String IMPORT_SESSION_VALIDATED = "IMPORT_SESSION_VALIDATED";

    // Employee CSV import confirmation (V2-006)
    public static final String IMPORT_SESSION_CONFIRMED = "IMPORT_SESSION_CONFIRMED";

    // Compensation (V2-006A)
    public static final String COMPENSATION_CREATED = "COMPENSATION_CREATED";
    public static final String COMPENSATION_REVISED = "COMPENSATION_REVISED";

    // Payroll run (V2-007)
    public static final String PAYROLL_RUN_CREATED = "PAYROLL_RUN_CREATED";

    // Work Calendar + assignment (V2-008A.3)
    public static final String WORK_CALENDAR_CREATED = "WORK_CALENDAR_CREATED";
    public static final String WORK_CALENDAR_UPDATED = "WORK_CALENDAR_UPDATED";
    public static final String WORK_CALENDAR_ASSIGNED = "WORK_CALENDAR_ASSIGNED";

    // Attendance exception (V2-008A.4)
    public static final String ATTENDANCE_EXCEPTION_CREATED = "ATTENDANCE_EXCEPTION_CREATED";
    public static final String ATTENDANCE_EXCEPTION_UPDATED = "ATTENDANCE_EXCEPTION_UPDATED";
    public static final String ATTENDANCE_EXCEPTION_DELETED = "ATTENDANCE_EXCEPTION_DELETED";

    // Leave entry (V2-008A.5)
    public static final String LEAVE_ENTRY_CREATED = "LEAVE_ENTRY_CREATED";
    public static final String LEAVE_ENTRY_CANCELLED = "LEAVE_ENTRY_CANCELLED";

    // Payroll-period inputs (V2-008A.6)
    public static final String VARIABLE_EARNING_CREATED = "VARIABLE_EARNING_CREATED";
    public static final String ARREAR_CREATED = "ARREAR_CREATED";

    // Payroll calculation (V2-008A)
    public static final String PAYROLL_CALCULATED = "PAYROLL_CALCULATED";
    public static final String PAYROLL_RECALCULATED = "PAYROLL_RECALCULATED";

    // Statutory rule-value store (Phase 2)
    public static final String STATUTORY_RULE_CREATED = "STATUTORY_RULE_CREATED";
    public static final String STATUTORY_RULE_UPDATED = "STATUTORY_RULE_UPDATED";
    public static final String STATUTORY_RULE_VERIFIED = "STATUTORY_RULE_VERIFIED";
    public static final String STATUTORY_RULE_SUPERSEDED = "STATUTORY_RULE_SUPERSEDED";

    public static final String ENTITY_USER = "app_user";
    public static final String ENTITY_COMPANY = "company";
    public static final String ENTITY_LEGAL_ENTITY = "legal_entity";
    public static final String ENTITY_STATUTORY_CONFIGURATION = "statutory_configuration";
    public static final String ENTITY_EMPLOYEE = "employee";
    public static final String ENTITY_EMPLOYEE_BANK_ACCOUNT = "employee_bank_account";
    public static final String ENTITY_EMPLOYEE_OPENING_TAX_STATE = "employee_opening_tax_state";
    public static final String ENTITY_EMPLOYEE_LEAVE_BALANCE = "employee_leave_balance";
    public static final String ENTITY_IMPORT_SESSION = "import_session";
    public static final String ENTITY_COMPENSATION_RECORD = "compensation_record";
    public static final String ENTITY_PAYROLL_RUN = "payroll_run";
    public static final String ENTITY_WORK_CALENDAR = "work_calendar";
    public static final String ENTITY_WORK_CALENDAR_ASSIGNMENT = "work_calendar_assignment";
    public static final String ENTITY_ATTENDANCE_EXCEPTION = "attendance_exception";
    public static final String ENTITY_LEAVE_ENTRY = "leave_entry";
    public static final String ENTITY_VARIABLE_EARNING = "variable_earning";
    public static final String ENTITY_ARREAR = "arrear";
    public static final String ENTITY_STATUTORY_PF_RULE = "statutory_pf_rule";
    public static final String ENTITY_STATUTORY_PT_RULE = "statutory_pt_rule";
    public static final String ENTITY_STATUTORY_TDS_RULE = "statutory_tds_rule";
}
