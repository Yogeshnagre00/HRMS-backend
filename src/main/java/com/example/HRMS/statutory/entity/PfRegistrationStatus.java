package com.example.HRMS.statutory.entity;

/**
 * PF registration status (Data Model 5.1; Business Rules 7.1).
 *
 * <p>Registration status is a separate concern from applicability: a company may
 * be PF-applicable yet not registered. An applicable-but-unresolved registration
 * state is a downstream blocking Health Check condition (out of scope here).
 */
public enum PfRegistrationStatus {
    REGISTERED,
    NOT_REGISTERED,
    VOLUNTARY_COVERAGE
}
