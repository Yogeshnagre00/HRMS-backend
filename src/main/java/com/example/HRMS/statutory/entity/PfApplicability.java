package com.example.HRMS.statutory.entity;

/**
 * PF applicability state (Data Model 5.1; Business Rules 7.1).
 *
 * <p>Applicability is explicit. {@code UNCONFIRMED} is a first-class stored
 * state, never a silent substitute for {@code NO} (or {@code YES}). Downstream
 * Health Check treats {@code UNCONFIRMED} as a blocking finding; that blocking
 * logic belongs to the payroll/health-check module, not this configuration slice.
 */
public enum PfApplicability {
    YES,
    NO,
    UNCONFIRMED
}
