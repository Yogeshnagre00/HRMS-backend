package com.example.HRMS.leave.entity;

/**
 * Payroll treatment of a {@link LeaveEntry} (Data Model 8.1).
 *
 * <p>v0 supports exactly two treatments. {@code PAID_LEAVE} consumes the
 * employee's current-FY paid-leave balance and creates no LOP;
 * {@code UNPAID_LOP_LEAVE} records explicit loss-of-pay and never touches the
 * balance. This is distinct from {@link LeaveTreatment} (the balance resource,
 * which is PAID_LEAVE only).
 */
public enum LeaveEntryTreatment {
    PAID_LEAVE,
    UNPAID_LOP_LEAVE
}
