/**
 * HRMS Payroll MVP backend — modular monolith root package.
 *
 * <p>The application is a single deployable Spring Boot service (no
 * microservices, no Kubernetes) organised into feature modules with clear
 * boundaries. Each business module owns its controllers, application/domain
 * services and persistence, and collaborates through application services
 * rather than reaching across module internals.
 *
 * <h2>Current packages</h2>
 * <ul>
 *   <li>{@code common}  — cross-cutting API contract (error/validation envelope)
 *       and shared building blocks.</li>
 *   <li>{@code health}  — versioned application liveness endpoint.</li>
 * </ul>
 *
 * <h2>Intended future module boundaries</h2>
 * These modules are defined by the approved architecture and Data Model but are
 * intentionally <em>not</em> implemented yet. They are listed here as the target
 * structure so future work lands in the right place; empty placeholder classes
 * are deliberately avoided.
 * <ul>
 *   <li>{@code company}      — Company &amp; LegalEntity configuration.</li>
 *   <li>{@code statutory}    — StatutoryConfiguration &amp; rule version sets.</li>
 *   <li>{@code calendar}     — WorkCalendar &amp; assignments.</li>
 *   <li>{@code employee}     — Employee master, bank, opening tax state.</li>
 *   <li>{@code attendance}   — Attendance exceptions.</li>
 *   <li>{@code leave}        — Leave entries &amp; balances.</li>
 *   <li>{@code compensation} — Compensation records, variable earnings, arrears.</li>
 *   <li>{@code payroll}      — Payroll lifecycle, with sub-modules:
 *       {@code payroll.calculation}, {@code payroll.healthcheck},
 *       {@code payroll.approval}, {@code payroll.output}.</li>
 * </ul>
 *
 * <p>Persistence is PostgreSQL with Flyway-managed migrations. Money uses fixed
 * decimal types; effective-dated business state is stored as records and never
 * overwritten.
 */
package com.example.HRMS;
