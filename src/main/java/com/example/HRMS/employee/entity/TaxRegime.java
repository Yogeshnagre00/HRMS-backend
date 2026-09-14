package com.example.HRMS.employee.entity;

/**
 * Employee tax regime (Data Model 7.1).
 *
 * <p>Both regimes are storable. v0 automatic TDS supports NEW_REGIME only;
 * OLD_REGIME employees remain importable/usable and are handled by a downstream
 * TDS Health Check finding (Business Rules §7.3) — not by this CRUD slice.
 */
public enum TaxRegime {
    NEW_REGIME,
    OLD_REGIME
}
