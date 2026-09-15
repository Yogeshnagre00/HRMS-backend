package com.example.HRMS.payroll.entity;

/**
 * Type of a {@link PayrollResultLine} (Data Model 10.3).
 *
 * <p>The non-statutory calculation (V2-008A) produces only {@code EARNING}
 * lines (fixed prorated components, variable earnings, arrears).
 * {@code DEDUCTION} and {@code EMPLOYER_CONTRIBUTION} are declared for the later
 * statutory slice and are not emitted here.
 */
public enum PayrollLineType {
    EARNING,
    DEDUCTION,
    EMPLOYER_CONTRIBUTION
}
