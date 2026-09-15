package com.example.HRMS.leave.entity;

/**
 * Leave treatment for a balance record (Data Model 8.2).
 *
 * <p>v0 supports only the balance-managed {@code PAID_LEAVE} treatment. Unpaid
 * Leave/LOP is not balance-managed and is out of scope for this resource. This
 * value is server-defined for the paid-leave balance endpoint and not selectable
 * by the client.
 */
public enum LeaveTreatment {
    PAID_LEAVE
}
