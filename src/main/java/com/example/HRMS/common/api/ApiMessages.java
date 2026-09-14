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

    // --- Employee opening tax state --------------------------------------
    public static final String OPENING_TAX_STATE_NOT_FOUND =
            "Opening tax state has not been set for the employee for the current financial year";
    public static final String OPENING_TAX_STATE_INCOMPLETE =
            "Both cumulativeTaxableIncome and tdsAlreadyDeducted are required to create "
                    + "the opening tax state";
    public static final String OPENING_TAX_STATE_CONFLICT =
            "Opening tax state already exists for the employee and financial year";

    // --- System ----------------------------------------------------------
    public static final String SYSTEM_UNEXPECTED_ERROR = "An unexpected error occurred";

    /** A reserved (non-assignable) role cannot be assigned; names the role code. */
    public static String roleNotAssignable(String roleCode) {
        return "Role '" + roleCode + "' is reserved and not assignable";
    }
}
