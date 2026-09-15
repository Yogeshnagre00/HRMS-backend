package com.example.HRMS.csvimport.validation;

/**
 * Stable, machine-readable codes for CSV validation issues (API §11.4). These
 * are the per-issue codes in the validation result's {@code issues} list —
 * distinct from the top-level HTTP {@link com.example.HRMS.common.api.ApiErrorCode}.
 * Clients branch on these codes, never on the human-readable message.
 */
public final class CsvIssueCodes {

    private CsvIssueCodes() {
    }

    // File / header level
    public static final String FILE_EMPTY = "FILE_EMPTY";
    public static final String FILE_UNREADABLE = "FILE_UNREADABLE";
    public static final String HEADER_MISSING = "HEADER_MISSING";
    public static final String HEADER_UNKNOWN = "HEADER_UNKNOWN";
    public static final String HEADER_DUPLICATE = "HEADER_DUPLICATE";
    public static final String HEADER_BLANK = "HEADER_BLANK";
    public static final String ROW_COLUMN_COUNT_MISMATCH = "ROW_COLUMN_COUNT_MISMATCH";

    // Row / field level
    public static final String REQUIRED_FIELD_MISSING = "REQUIRED_FIELD_MISSING";
    public static final String INVALID_EMPLOYEE_ID = "INVALID_EMPLOYEE_ID";
    public static final String DUPLICATE_EMPLOYEE_ID_IN_FILE = "DUPLICATE_EMPLOYEE_ID_IN_FILE";
    public static final String INVALID_NAME = "INVALID_NAME";
    public static final String INVALID_DATE = "INVALID_DATE";
    public static final String EXIT_BEFORE_JOINING = "EXIT_BEFORE_JOINING";
    public static final String INVALID_EMPLOYMENT_TYPE = "INVALID_EMPLOYMENT_TYPE";
    public static final String INVALID_PAN = "INVALID_PAN";
    public static final String INVALID_UAN = "INVALID_UAN";
    public static final String INVALID_PT_STATE = "INVALID_PT_STATE";
    public static final String INVALID_TAX_REGIME = "INVALID_TAX_REGIME";
    public static final String OLD_REGIME_TDS_UNSUPPORTED = "OLD_REGIME_TDS_UNSUPPORTED";
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String NEGATIVE_AMOUNT = "NEGATIVE_AMOUNT";
    public static final String INVALID_IFSC = "INVALID_IFSC";
    public static final String INVALID_ACCOUNT_NUMBER = "INVALID_ACCOUNT_NUMBER";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String NEGATIVE_QUANTITY = "NEGATIVE_QUANTITY";
}
