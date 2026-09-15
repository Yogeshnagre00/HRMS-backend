package com.example.HRMS.compensation.entity;

/**
 * Server-controlled provenance of a {@link CompensationRecord} (Data Model 9.1;
 * API §14.1). The client never supplies this value. {@code MANUAL} is assigned
 * by the compensation API; {@code CSV_IMPORT} is reserved for the later V2-006
 * CSV confirmation flow (not implemented here).
 */
public enum CompensationSource {
    MANUAL,
    CSV_IMPORT
}
