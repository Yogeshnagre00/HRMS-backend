package com.example.HRMS.common.api;

/**
 * Centralized human-readable API response/error messages.
 *
 * <p>This is the single source of client-facing message text used across the
 * backend, so wording stays consistent and is not scattered across controllers,
 * services and handlers. Machine-readable identifiers live separately in
 * {@link ApiErrorCode}; response structure in {@link ApiError}. Internal logging,
 * debug and developer-only messages are intentionally NOT defined here.
 */
public final class ApiMessages {

    private ApiMessages() {
    }

    // --- Authentication --------------------------------------------------
    public static final String AUTH_INVALID_CREDENTIALS = "Invalid username or password";
    public static final String AUTH_AUTHENTICATION_REQUIRED = "Authentication required";
    public static final String AUTH_REQUIRED_TO_ACCESS =
            "Authentication is required to access this resource";
    public static final String AUTH_CURRENT_PASSWORD_INCORRECT = "Current password is incorrect";

    // MFA
    public static final String AUTH_MFA_CHALLENGE_INVALID_OR_EXPIRED =
            "Invalid or expired MFA challenge";
    public static final String AUTH_MFA_CHALLENGE_TOKEN_INVALID = "Invalid MFA challenge token";
    public static final String AUTH_MFA_CHALLENGE_INVALID = "Invalid MFA challenge";
    public static final String AUTH_MFA_CODE_INVALID = "Invalid MFA code";

    // Refresh token
    public static final String AUTH_REFRESH_TOKEN_INVALID = "Invalid refresh token";
    public static final String AUTH_REFRESH_TOKEN_EXPIRED_OR_REVOKED =
            "Refresh token is expired or revoked";
    public static final String AUTH_REFRESH_TOKEN_NO_LONGER_VALID =
            "Refresh token is no longer valid";

    // --- Authorization ---------------------------------------------------
    public static final String AUTHORIZATION_FORBIDDEN =
            "You do not have permission to perform this action";
    public static final String AUTHORIZATION_SUPER_ADMIN_PLATFORM_ONLY =
            "Only a platform administrator may assign SUPER_ADMIN";

    // --- Validation ------------------------------------------------------
    public static final String VALIDATION_FAILED_FIELDS =
            "Validation failed for one or more fields";
    public static final String VALIDATION_FAILED_PARAMETERS =
            "Validation failed for one or more parameters";
    public static final String VALIDATION_ROLE_IDS_INVALID = "One or more role ids are invalid";

    // --- Resource / RBAC -------------------------------------------------
    public static final String ROLE_NOT_FOUND = "Role not found";
    public static final String USER_NOT_FOUND = "User not found";

    // --- Company ---------------------------------------------------------
    public static final String COMPANY_NOT_FOUND = "Company was not found";
    public static final String COMPANY_ALREADY_EXISTS =
            "An active company already exists; v0 supports a single company";

    // --- Legal Entity ----------------------------------------------------
    public static final String LEGAL_ENTITY_NOT_FOUND = "Legal entity was not found";
    public static final String LEGAL_ENTITY_ALREADY_EXISTS =
            "An active legal entity already exists for this company; "
                    + "v0 supports a single legal entity per company";

    // --- Statutory Configuration -----------------------------------------
    public static final String STATUTORY_CONFIG_NOT_FOUND =
            "Statutory configuration has not been set for the legal entity";
    public static final String STATUTORY_RULE_VERSION_SET_NOT_FOUND =
            "Statutory rule version set was not found";
    public static final String STATUTORY_RULE_VERSION_SET_REQUIRED =
            "A valid statutory rule version set must be referenced";
    public static final String STATUTORY_PF_REGISTRATION_REQUIRED =
            "PF registration status is required when PF is applicable";
    public static final String STATUTORY_PF_REGISTRATION_NOT_ALLOWED =
            "PF registration details are only allowed when PF is applicable";
    public static final String STATUTORY_EFFECTIVE_RANGE_INVALID =
            "effectiveTo must be on or after effectiveFrom";

    /** Names the unsupported PT state and the supported set (Business Rules §7.2). */
    public static String ptStateUnsupported(String state) {
        return "PT state '" + state + "' is not supported in v0; supported states are "
                + "Maharashtra, Karnataka, Tamil Nadu, Telangana, West Bengal";
    }

    // --- Employee --------------------------------------------------------
    public static final String EMPLOYEE_NOT_FOUND = "Employee was not found";
    public static final String EMPLOYEE_ID_ALREADY_EXISTS =
            "An employee with this Employee ID already exists in the legal entity";
    public static final String EMPLOYEE_EXIT_BEFORE_JOINING =
            "exitDate must be on or after joiningDate";

    // --- Employee bank account -------------------------------------------
    public static final String BANK_ACCOUNT_NOT_FOUND =
            "Bank details have not been set for the employee";

    // --- Employee leave balance ------------------------------------------
    public static final String LEAVE_BALANCE_NOT_FOUND =
            "Leave balance has not been set for the employee for the current financial year";
    public static final String LEAVE_BALANCE_CONFLICT =
            "Leave balance already exists for the employee and financial year";

    // --- Compensation (V2-006A) ------------------------------------------
    public static final String COMPENSATION_NOT_FOUND = "Compensation record was not found";
    public static final String COMPENSATION_ALREADY_EXISTS =
            "A compensation record already exists for the employee; use a revision to change it";
    public static final String COMPENSATION_NO_CURRENT_TO_REVISE =
            "No current compensation exists to revise for the employee";
    public static final String COMPENSATION_EFFECTIVE_DATE_OVERLAP =
            "The revision effective date overlaps an existing compensation record";

    // --- Employee opening tax state --------------------------------------
    public static final String OPENING_TAX_STATE_NOT_FOUND =
            "Opening tax state has not been set for the employee for the current financial year";
    public static final String OPENING_TAX_STATE_INCOMPLETE =
            "Both cumulativeTaxableIncome and tdsAlreadyDeducted are required to create "
                    + "the opening tax state";
    public static final String OPENING_TAX_STATE_CONFLICT =
            "Opening tax state already exists for the employee and financial year";

    // --- Employee CSV validation (V2-005) --------------------------------
    public static final String CSV_FILE_REQUIRED = "A CSV file is required";
    public static final String CSV_FILE_EMPTY = "The uploaded CSV file is empty";
    public static final String CSV_FILE_UNREADABLE = "The uploaded CSV file could not be read";
    public static final String CSV_IMPORT_SESSION_NOT_FOUND = "Import session was not found";
    public static final String CSV_HEADER_MISSING = "Required header is missing";
    public static final String CSV_HEADER_UNKNOWN = "Unknown header is not allowed";
    public static final String CSV_HEADER_DUPLICATE = "Duplicate header is not allowed";
    public static final String CSV_HEADER_BLANK = "Blank header name is not allowed";
    public static final String CSV_ROW_COLUMN_COUNT_MISMATCH =
            "Row column count does not match the header";
    public static final String CSV_REQUIRED_FIELD_MISSING = "Required value is missing";
    public static final String CSV_DUPLICATE_EMPLOYEE_ID_IN_FILE =
            "Duplicate Employee ID within the uploaded file";
    public static final String CSV_INVALID_DATE = "Invalid date; expected format YYYY-MM-DD";
    public static final String CSV_EXIT_BEFORE_JOINING = "Exit Date must be on or after Joining Date";
    public static final String CSV_INVALID_PAN = "Invalid PAN format";
    public static final String CSV_INVALID_TAX_REGIME = "Tax Regime must be NEW_REGIME or OLD_REGIME";
    public static final String CSV_OLD_REGIME_TDS_UNSUPPORTED =
            "Old Tax Regime is accepted for import, but automatic v0 TDS is unsupported "
                    + "and will be surfaced as a blocking Health Check finding";
    public static final String CSV_INVALID_AMOUNT = "Invalid monetary value";
    public static final String CSV_NEGATIVE_AMOUNT = "Monetary value must be non-negative";
    public static final String CSV_INVALID_QUANTITY = "Invalid numeric quantity";
    public static final String CSV_NEGATIVE_QUANTITY = "Quantity must be non-negative";
    public static final String CSV_INVALID_IFSC = "Invalid IFSC";
    public static final String CSV_INVALID_ACCOUNT_NUMBER = "Invalid account number";
    public static final String CSV_INVALID_PT_STATE =
            "PT State is not supported in v0 (Maharashtra, Karnataka, Tamil Nadu, "
                    + "Telangana, West Bengal)";

    // --- System ----------------------------------------------------------
    public static final String SYSTEM_UNEXPECTED_ERROR = "An unexpected error occurred";

    /** A reserved (non-assignable) role cannot be assigned; names the role code. */
    public static String roleNotAssignable(String roleCode) {
        return "Role '" + roleCode + "' is reserved and not assignable";
    }
}
