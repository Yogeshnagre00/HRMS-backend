package com.example.HRMS.leave.entity;

/**
 * Lifecycle status of a {@link LeaveEntry} (Data Model 8.1).
 *
 * <p>{@code RECORDED} is an active entry; {@code CANCELLED} is a reversed entry.
 * A PAID_LEAVE entry consumes balance on RECORDED and reverses that consumption
 * on transition to CANCELLED (V2-008A.2). Cancellation is one-way and idempotent
 * at the balance level (cancelling an already-CANCELLED entry does not reverse
 * again).
 */
public enum LeaveEntryStatus {
    RECORDED,
    CANCELLED
}
