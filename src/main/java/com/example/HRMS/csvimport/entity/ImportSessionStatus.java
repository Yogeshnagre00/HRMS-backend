package com.example.HRMS.csvimport.entity;

/**
 * Lifecycle status of an {@link ImportSession} (Data Model 7.4).
 *
 * <p>V2-005 creates only {@code VALIDATED} sessions. {@code CONFIRMED} is set by
 * V2-006 after explicit confirmation; V2-005 does not implement confirmation.
 */
public enum ImportSessionStatus {
    VALIDATED,
    CONFIRMED
}
