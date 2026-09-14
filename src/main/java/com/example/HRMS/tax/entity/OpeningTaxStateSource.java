package com.example.HRMS.tax.entity;

/**
 * Source of an {@link EmployeeOpeningTaxState} row (Data Model 7.2).
 *
 * <p>{@code CSV_IMPORT} is assigned only by the CSV import flow (a later slice);
 * the dedicated manual API assigns {@code MANUAL}. The client never supplies or
 * overrides this value.
 */
public enum OpeningTaxStateSource {
    CSV_IMPORT,
    MANUAL
}
