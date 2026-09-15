package com.example.HRMS.csvimport.validation;

import java.util.List;

/**
 * The canonical employee-CSV header set, in exact order (API §11.2, Data Model
 * template, Screen S07). These exact human-readable strings are the contract;
 * header matching is exact and case-sensitive (no normalization). This is the
 * single in-code definition of the header contract.
 */
public final class CsvHeaders {

    private CsvHeaders() {
    }

    public static final String EMPLOYEE_ID = "Employee ID";
    public static final String NAME = "Name";
    public static final String JOINING_DATE = "Joining Date";
    public static final String EXIT_DATE = "Exit Date";
    public static final String EMPLOYMENT_TYPE = "Employment Type";
    public static final String DEPARTMENT = "Department";
    public static final String DESIGNATION = "Designation";
    public static final String LOCATION = "Location";
    public static final String PAN = "PAN";
    public static final String UAN = "UAN";
    public static final String PT_STATE = "PT State";
    public static final String TAX_REGIME = "Tax Regime";
    public static final String CUMULATIVE_TAXABLE_INCOME = "Current-FY Cumulative Taxable Income";
    public static final String TDS_ALREADY_DEDUCTED = "Current-FY TDS Already Deducted";
    public static final String ACCOUNT_NUMBER = "Account Number";
    public static final String IFSC = "IFSC";
    public static final String CTC = "CTC";
    public static final String BASIC = "Basic";
    public static final String HRA = "HRA";
    public static final String OTHER_ALLOWANCES = "Other Allowances";
    public static final String EFFECTIVE_DATE = "Effective Date";
    public static final String OPENING_LEAVE_BALANCE = "Opening Leave Balance";

    /** Canonical order (also the download template order). */
    public static final List<String> CANONICAL = List.of(
            EMPLOYEE_ID, NAME, JOINING_DATE, EXIT_DATE, EMPLOYMENT_TYPE, DEPARTMENT,
            DESIGNATION, LOCATION, PAN, UAN, PT_STATE, TAX_REGIME,
            CUMULATIVE_TAXABLE_INCOME, TDS_ALREADY_DEDUCTED, ACCOUNT_NUMBER, IFSC,
            CTC, BASIC, HRA, OTHER_ALLOWANCES, EFFECTIVE_DATE, OPENING_LEAVE_BALANCE);

    /** 0-based index of a header in canonical order (for deterministic issue ordering). */
    public static int position(String header) {
        int idx = CANONICAL.indexOf(header);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }
}
