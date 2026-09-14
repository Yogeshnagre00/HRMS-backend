package com.example.HRMS.statutory.entity;

/**
 * TDS policy for the configuration (Data Model 5.1; Business Rules 7.3).
 *
 * <p>v0 automatic TDS supports the New Tax Regime only; the single supported
 * value identifies that policy explicitly. Old-Regime automatic TDS is
 * unsupported in v0 and is handled at the employee/health-check level later —
 * it is deliberately not a configuration option here.
 */
public enum TdsPolicy {
    NEW_REGIME_AUTOMATIC_V0
}
