# AGENTS.md — HRMS Payroll MVP (Backend)

Canonical engineering-governance document for AI coding agents working on this
repository. This is the single source of engineering rules. Other agent
instruction mechanisms (e.g. Kiro steering) must reference this file rather than
duplicate it.

This file was written after inspecting the actual repository. Keep it accurate:
when the stack, structure, or rules change, update this file in the same task.

---

## 1. Purpose and Project Context

This repository is the backend for the **HRMS Payroll MVP v0.1** — a controlled,
admin-operated monthly payroll system for **one Indian company, one Indian legal
entity**, primarily salaried employees, on a standard 5-day calendar, with
**PF + PT + New-Regime automatic TDS**, producing a Payroll Register, payslips,
and a generic bank-transfer CSV.

The MVP is delivered as a **modular monolith**, built incrementally in numbered
tasks (V0-001, V0-002, …). Each task is bounded. Agents implement exactly the
assigned task and stop.

Out of scope for v0 (do not build unless a future approved task requires it):
ESS/MSS, employee/manager login, attendance check-in/out, ESI/LWF, overtime,
shifts, six-day/rotational calendars, international payroll, bank payment
integration, historical payroll migration, F&F/gratuity/leave-encashment/bonus,
loans/advances, recruitment/performance.

## 2. Source-of-Truth Hierarchy

Product behavior is defined by the specification documents, **not** invented by
agents. The documents live at `../docs/payroll-mvp/` (one level above this
backend repo, in the workspace root):

| # | Document | Authority |
|---|----------|-----------|
| 01 | `01_HRMS_MVP_PRD_V1.0.md` | Vision / high-level PRD |
| 02 | `02_HRMS_Payroll_MVP_v0.1_PRD.md` | v0.1 PRD |
| 03 | `03_HRMS_Payroll_MVP_v0.3_Business_Rules_Final.md` | Business rules |
| 04 | `04_HRMS_Payroll_MVP_v0.1_Screen_User_Journey_Specification.md` | Screens / journeys |
| 05 | `05_HRMS_Payroll_MVP_v0.1_Data_Model_Entity_Specification.md` | Data model (authoritative for schema) |
| 06 | `06_HRMS_Payroll_MVP_v0.1_API_Backend_Requirements_Specification.md` | API/backend contract |
| 07 | `07_HRMS_Payroll_MVP_v0.1_Acceptance_Test_Specification.md` | Acceptance tests |
| 08 | `08_HRMS_Payroll_MVP_v0.1_Solo_Vibe_Coding_Implementation_Plan.md` | Task sequencing (V0-00x) |

Rules:

- Agents **must read** the relevant document(s) before implementing behavior.
- Agents **must not invent** product behavior that the documents define.
- When documents **conflict materially**, do not silently pick a behavior —
  **STOP and report** the conflict (see Stop Conditions).

### 2.1 Known stale-OPEN documentation issue (authoritative resolution)

Business Rules v0.3 (doc 03) still contains **stale "OPEN — must be decided"**
text in its earlier sections and decision log (e.g. §5.2 Proration, §6.6
Rounding, and the decision-log table) for several load-bearing decisions. Those
decisions were subsequently **closed** by the later artifacts — the **Data Model
(05)** and **API/Backend (06)** specifications.

The **closed** decisions in 05/06 are the **active rules**. Do **not** resurrect
the stale OPEN text. Specifically:

| Decision | Active rule | Where closed |
|----------|-------------|--------------|
| Proration basis | **Calendar-day** proration for fixed recurring components | 05 §9.1, 06 §16 |
| LOP basis | Same **calendar-day** basis as salary proration | 05 §9.1, 06 §16 |
| Final rounding | Round final **employee monetary values to 2 decimals at the result boundary**; keep higher precision only in intermediate calculations | 05 §10.2, §19; 06 §16 |
| Leave treatments | Exactly **two**: `PAID_LEAVE` and `UNPAID_LOP_LEAVE` | 05 §8.1 |
| Zero net pay | **Valid** only under a genuine calculation condition; raises a Health Check review finding | 05 §10.2, 06 §16 |
| Negative net pay | **Always blocking**; never capped to zero | 05 §10.2, 06 §16, 03 §6.5 |

If any *other* apparently-open decision blocks implementation, STOP and report.

## 3. Current Technology Stack

Verified from `pom.xml` and the codebase (do not assume beyond this):

- **Java 21**
- **Spring Boot 4.1.1** (Spring Framework 7). Note Boot 4 starter renames:
  `spring-boot-starter-webmvc` (was `-web`), `spring-boot-starter-webmvc-test`.
- **Maven** (wrapper `mvnw`; a system Maven 3.9.x on JDK 21 also works).
- **PostgreSQL** (dev/prod). **H2** (test scope) in PostgreSQL-compatibility mode.
- **Flyway** (`spring-boot-starter-flyway` + `flyway-database-postgresql`).
- **springdoc-openapi 3.1.1** (`springdoc-openapi-starter-webmvc-ui`) — the 3.x
  line targets Spring Boot 4 / Jackson 3.
- **Lombok** (optional, annotation processor configured).
- Actuator for health/readiness.

Do not add frameworks or dependencies without a current, task-driven need.

## 4. Repository Structure

```
HRMS-backend/                     <- this git repository / project root
  AGENTS.md                       <- this file (canonical rules)
  pom.xml
  src/main/java/com/example/HRMS/
    HrmsApplication.java
    package-info.java             <- documents module boundaries
    common/api/                   <- shared API contract (ApiError, handler, OpenAPI config)
    health/                       <- versioned liveness endpoint
  src/main/resources/
    application.properties        <- base config (profile activation, flyway, actuator, error contract)
    application-local.properties  <- local PostgreSQL profile (env-var creds)
    application-prod.properties    <- production profile (env-driven)
    db/migration/
      V1__baseline.sql            <- V0-001 baseline (no business tables)
      V2__configuration_master_tables.sql  <- V0-002 six config tables
    logback-spring.xml
  src/test/java/com/example/HRMS/ <- mirrors main packages
  src/test/resources/
    application-test.properties         <- H2 test profile (default test DB)
    application-postgres-it.properties  <- opt-in PostgreSQL integration profile
../docs/payroll-mvp/              <- specifications (workspace root, not in this repo)
```

The `HRMS-Frontend-` repository (workspace root) is separate and out of scope
for backend tasks.

## 5. Architecture Rules

- The project is a **modular monolith**. Keep it that way.
- Do **not** introduce: microservices, distributed systems, Kubernetes,
  event-driven infrastructure, messaging systems, or generic frameworks —
  unless a future approved task explicitly requires them.
- Prefer: clear module boundaries, thin controllers, simple application
  services, deterministic domain logic, explicit dependencies, small focused
  classes, testable business logic.
- Do **not** build abstractions for hypothetical future requirements.
- Layering: `HTTP/REST controller → application service → domain → repository →
  PostgreSQL`. Controllers are thin; repositories are not called directly by
  controllers; persistence entities are not exposed as API contracts (use DTOs).

## 6. Module Boundaries

Current packages: `common`, `health`.

Intended future business modules (defined by architecture; **not** implemented
yet — do not create empty placeholders): `company`, `statutory`, `calendar`,
`employee`, `attendance`, `leave`, `compensation`, `payroll`
(`payroll.calculation`, `payroll.healthcheck`, `payroll.approval`,
`payroll.output`).

Each business module owns its controllers, services, and persistence, and
collaborates through application services — never by reaching into another
module's internals.

## 7. Coding Standards

- Follow standard Java + Spring conventions already present in the repo.
- Small, single-responsibility classes and methods; clear names.
- Constructor injection; avoid field injection.
- Immutable DTOs (Java records) where practical (see `common/api/ApiError`).
- Match existing formatting. No unrelated reformatting.
- Every production class/method/field must have a **current** reason to exist.
- Javadoc on non-trivial public types explaining *why*, not just *what*.

## 8. Database and Migration Rules

- **PostgreSQL** is the dev/prod database; **Flyway** is the only schema-change
  mechanism.
- **Never modify or delete an already-applied migration** (`V1__baseline.sql`,
  `V2__configuration_master_tables.sql` are applied — treat as immutable).
- Every schema change is a **new** versioned migration. Preserve ordering.
- Never bypass Flyway with manual production schema changes.
- Migrations must be deterministic, repeatable from a clean database, and
  **PostgreSQL-compatible while also running on H2** (the test DB). Practical
  consequences learned in V0-002:
  - Use `VARCHAR + CHECK` for enum domains, **not** native PostgreSQL `ENUM`
    types (H2 does not support them).
  - Do **not** use partial/filtered unique indexes (`... WHERE ...`) in shared
    migrations — H2 does not support them and fails silently in PostgreSQL mode.
    Enforce conditional invariants (e.g. "one active per entity", "no
    overlapping effective ranges") in the **service layer**, with tests.
- Use appropriate **PK / FK / NOT NULL / UNIQUE / CHECK** constraints and
  indexes on foreign keys and important lookup fields.
- Do **not** create speculative tables or future Employee/payroll tables early.
- When a column must reference a table not yet created (e.g. `created_by` → a
  future users table), model it as the correct typed column and add the FK in
  the migration that introduces the target table. Document the deferral.
- **Money uses fixed-precision `NUMERIC`** (e.g. `NUMERIC(18,2)`), never binary
  floating point.
- Business invariants require automated tests even when enforced at service
  level. **Do not weaken a database constraint just to make a test pass.**

## 9. API Standards

- Versioned base path `/api/v1`. ISO dates (`YYYY-MM-DD`), ISO-8601 date-times.
- One consistent success/error contract across modules (`common/api/ApiError`
  via `GlobalExceptionHandler`).
- Every endpoint: request validation, server-side authorization (when security
  exists), consistent error behavior, appropriate HTTP status, OpenAPI docs,
  and tests.
- Use DTOs at API boundaries; do not expose persistence entities.
- Do **not** create endpoints, DTOs, or OpenAPI entries that the current task
  does not require. Do not document APIs that do not exist.

## 10. Security Standards

- **Never commit** passwords, API keys, tokens, private keys, DB credentials, or
  any secret — not in source, properties, or git history.
- Credentials come from environment variables. Current DB vars:
  `HRMS_DB_USERNAME`, `HRMS_DB_PASSWORD` (optional `HRMS_DB_URL`). No default
  username/password is committed; a missing var must fail fast.
- The application connects as the dedicated DB role **`hrms_app`**, never the
  PostgreSQL superuser `postgres`.
- Authorization (once implemented in V0-003+) is enforced **server-side**; never
  rely on frontend visibility.
- Do not disable security temporarily and leave the bypass in place.
- Security-sensitive operations require auditability (per Data Model §15).
- RBAC/authentication is **V0-003**; do not implement it in earlier tasks.

## 11. Payroll Correctness Rules

Payroll is the highest-risk area. Agents **must not invent** statutory rates, PF
rules, PT slabs, TDS values, tax calculations, statutory applicability,
effective dates, or legal interpretations. Statutory values are **verified,
versioned release inputs** (Data Model §5.2, §23) — never hardcoded from memory
or search.

Never:
- silently default statutory applicability (`UNCONFIRMED` is an explicit stored
  state, never treated as `NO`);
- silently convert unsupported scenarios (e.g. Old-Regime automatic TDS is
  unsupported → blocking finding, not a guess);
- silently convert missing required data to zero;
- silently ignore calculation errors;
- silently cap negative net pay to zero (**always blocking**);
- overwrite locked payroll.

Payroll calculations must be **deterministic and testable**. Apply the closed
decisions in §2.1 (calendar-day proration, final 2-decimal rounding, two leave
treatments, zero/negative net-pay handling). If a required statutory value is
not verified in the source material, **STOP and report**.

## 12. Testing Standards

- Use the lowest useful level first: unit → service/application →
  repository/integration → API → end-to-end.
- Prove behavior, not coverage numbers. For business rules include: normal,
  boundary, invalid, negative, and (when relevant) regression cases.
- **`mvn clean test` must pass** before declaring a task complete, unless the
  task explicitly documents a different command.
- The default test suite uses **H2** and must **not** depend on a developer's
  PostgreSQL install or password.
- PostgreSQL integration tests are **isolated and opt-in**: profile
  `postgres-it`, gated by `@EnabledIfEnvironmentVariable(HRMS_DB_USERNAME)`, so
  they are skipped in the normal suite. Run them with:
  ```
  # PowerShell
  $env:HRMS_DB_USERNAME="hrms_app"; $env:HRMS_DB_PASSWORD="<local password>"
  mvn "-Dtest=PostgresIntegrationSmokeTest" test
  ```
- For database tasks, verify against real PostgreSQL where appropriate.

## 13. Error Handling

- All API errors go through the shared `ApiError` contract via
  `GlobalExceptionHandler` (validation, constraint, and generic fallback).
- Do not leak stack traces or internal messages to clients
  (`server.error.include-*` are disabled by default).
- Fail fast and explicitly; never swallow exceptions silently. Business-blocking
  conditions surface as findings/validation errors, not silent success.

## 14. Logging and Observability

- Logging baseline is `logback-spring.xml` with per-profile levels.
- No secrets, credentials, or full PII in logs.
- No stray `System.out`/`printStackTrace`/debug logging left in committed code.
- Health/readiness: `/api/v1/health` (app liveness) and Actuator
  `/actuator/health` (+ liveness/readiness probe groups). Keep both working.
- Audit material actions per Data Model §15 once those features exist.

## 15. Configuration and Secrets

- Profiles: `local` (PostgreSQL, dev), `test` (H2, default test), `prod`
  (env-driven), `postgres-it` (opt-in PostgreSQL integration tests).
- Active profile defaults to `local`; override with `SPRING_PROFILES_ACTIVE`.
- All secrets via environment variables (see §10). Never commit credentials.
- OpenAPI JSON at `/v3/api-docs`; Swagger UI at `/swagger-ui.html`.

## 16. Dependency Management

- Versions are managed by the Spring Boot BOM where possible; pin explicit
  versions only when unmanaged (e.g. springdoc `3.1.1`).
- Add a dependency only with a current, task-driven need. Prefer well-known,
  maintained libraries. Watch for typosquatting.
- Do not upgrade/downgrade Spring Boot or other core versions as a side effect
  of an unrelated task.

## 17. Performance Principles

- Correctness and determinism first; payroll must be reproducible.
- Index foreign keys and important lookup columns.
- Avoid N+1 queries; page list endpoints (per API spec).
- Do not add caching, async, or batching infrastructure speculatively — only
  where a current requirement genuinely benefits (Implementation Plan §3).

## 18. Git / Change-Control Rules

- Before implementing: run `git status`.
- After implementing: run `git diff` and `git status`; review every change.
- Never overwrite unrelated user changes. No drive-by refactoring.
- Never rename or modify applied migrations.
- If a refactor is genuinely required for the task: explain why, keep it
  minimal, and test it.
- Do not modify unrelated files, business rules, the data model, or API
  behavior outside the task's scope.
- Commit only when explicitly asked; stage specific files, not `git add .`.

## 19. Scope-Control Rules

- Implement **only** the assigned task. Identify its exact scope and acceptance
  criteria before coding.
- The phrase "while I am here, I will also…" is **prohibited**.
- Do not start the next task automatically. Do not implement V0-003 (RBAC) or any
  business feature during infrastructure/governance tasks.
- A task-specific prompt may add requirements but must not silently contradict
  this document. If it conflicts, **STOP and report**.

## 20. Dead-Code Prevention

Before finishing, inspect for and remove: unused imports, classes, methods,
fields, dependencies; duplicate/unreachable code; placeholder implementations;
TODOs without an approved follow-up; debug statements; commented-out
implementation. **Do not keep dead code "for later."** Every production artifact
must have a current reason to exist.

## 21. Implementation Agent Protocol

The implementation agent implements the explicitly assigned task, may modify
code only as the task requires, follows this document, tests and verifies its own
work, and stops when the task is complete.

Per task:
1. Read the task.
2. Inspect the repository.
3. Read the relevant source-of-truth documents.
4. Identify exact scope.
5. Identify acceptance criteria.
6. Check for existing implementation (do not duplicate).
7. Implement the **smallest correct** solution.
8. Add/update tests.
9. Run tests (`mvn clean test`; PostgreSQL integration where relevant).
10. Inspect the diff (`git diff`).
11. Check for dead code (§20).
12. Report exactly what changed (§25).
13. **Stop.** Never continue into the next feature.

## 22. Verification Agent Protocol

The verification agent is **READ-ONLY**. It independently reviews the
implementation and **must not modify** source code, tests, migrations,
configuration, or documentation. It may report required corrections for the
implementation agent. Keep the two roles separate — never mix them.

It must independently inspect:
1. Task requirements
2. Source-of-truth documents
3. Git diff
4. Changed files
5. Dependencies
6. Database migrations
7. Tests
8. Security implications
9. Scope creep
10. Dead code

Then run appropriate checks: compilation, unit tests, integration tests, API
tests, migration verification, build.

It must produce a verdict:
- **PASS** — task requirements met, matches source-of-truth, tests/build pass,
  no scope creep, no dead code, no secrets.
- **FAIL** — one or more requirements not met. Each failure must include: the
  exact issue, affected file, evidence (command output/line references), and the
  required correction. **Do not fix it** — report it.
- **BLOCKED** — cannot verify (e.g. missing credentials, unresolved
  specification conflict, environment unavailable). State exactly what is needed.

## 23. Definition of Done

A task is DONE only when:
- requested functionality exists and behavior matches the source-of-truth docs;
- no unrelated functionality was added;
- tests exist and pass; relevant integration tests pass; build passes;
- no known critical issue remains;
- no secrets are committed;
- no dead production code remains;
- the `git diff` has been reviewed;
- the verification agent has independently reviewed it when required;
- a final implementation report (§25) is produced.

"Code compiles" alone is **not** Definition of Done.

## 24. Stop Conditions

STOP and report instead of guessing when:
- source documents conflict materially;
- statutory/legal behavior is unclear or a required statutory value is
  unverified;
- the database model conflicts with business rules;
- required credentials are unavailable;
- security design is ambiguous;
- a change would require modifying an already-applied migration;
- requested behavior requires an unsupported architectural change;
- the task would require scope beyond the current task;
- existing user changes would be overwritten;
- tests reveal an unresolved correctness issue.

## 25. Required Final Report

Every implementation task must report:
1. Task completed
2. Files added
3. Files modified
4. Files deleted (if any)
5. Behavior implemented
6. Tests added/changed
7. Tests executed and results (never claim a test passed unless it was actually run)
8. Build result
9. Database/migration changes
10. Security considerations
11. Scope verification
12. Dead-code check
13. Remaining issues
14. Specification ambiguities
15. Exact next recommended task
