package com.example.HRMS.statutory.service;

import java.util.Set;

/**
 * The Professional Tax states supported in v0 (Business Rules §7.2; PRD).
 *
 * <p>This is the exact approved v0 set — not a guess and not a statutory rate.
 * Slabs, rates and effective dates for these states remain separately verified
 * statutory inputs and are not represented here. An unsupported PT state must be
 * an explicit, blocking condition — never a silent zero — so configuration
 * rejects an unsupported state rather than accepting it and defaulting.
 */
public final class SupportedPtStates {

    private SupportedPtStates() {
    }

    public static final Set<String> STATES = Set.of(
            "Maharashtra",
            "Karnataka",
            "Tamil Nadu",
            "Telangana",
            "West Bengal");

    public static boolean isSupported(String state) {
        return state != null && STATES.contains(state);
    }
}
