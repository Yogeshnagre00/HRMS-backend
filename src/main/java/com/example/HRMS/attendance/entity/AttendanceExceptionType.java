package com.example.HRMS.attendance.entity;

/**
 * Type of an {@link AttendanceException} (Data Model 8.3).
 *
 * <p>v0 supports exactly these exception types. {@code FULL_DAY_ABSENCE} is a
 * full-day (1.0) absence, {@code HALF_DAY} is a half-day (0.5), and {@code LOP}
 * is an explicit loss-of-pay input (0.5 or 1.0). The type↔quantity relationship
 * is enforced in the service layer.
 */
public enum AttendanceExceptionType {
    FULL_DAY_ABSENCE,
    HALF_DAY,
    LOP
}
