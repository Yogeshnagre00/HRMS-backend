---
inclusion: always
---

# Engineering Governance — pointer to canonical rules

The canonical engineering-governance rulebook for this repository is
**`AGENTS.md`** at the backend repository root (`HRMS-backend/AGENTS.md`).

There is intentionally **one** canonical source of engineering rules. This
steering file is a thin compatibility layer for Kiro and deliberately does not
duplicate those rules. Always read and follow `AGENTS.md`.

Highest-priority reminders (full detail in `AGENTS.md`):

- Source of truth is `../docs/payroll-mvp/`. Do not invent product behavior.
  Where Business Rules v0.3 still says "OPEN", the Data Model (05) and
  API/Backend (06) specs close those decisions — see `AGENTS.md` §2.1.
- Modular monolith only. No speculative/dead code, tables, endpoints, or
  abstractions. Every artifact needs a current reason to exist.
- Never modify an already-applied Flyway migration (`V1`, `V2`). Every schema
  change is a new migration. Money uses fixed-precision `NUMERIC`.
- Never invent statutory values (PF/PT/TDS). Never silently default
  applicability, drop data to zero, or cap negative net pay. STOP and report.
- Never commit secrets. DB creds come from `HRMS_DB_USERNAME` /
  `HRMS_DB_PASSWORD`; the app connects as `hrms_app`, never `postgres`.
- `mvn clean test` must pass (H2, no PostgreSQL password required). PostgreSQL
  integration tests are opt-in (`postgres-it` profile).
- Implement only the assigned task, then stop. Do not implement V0-003 (RBAC)
  or any business feature during infrastructure/governance tasks.
- Keep the Implementation Agent (writes code) and Verification Agent
  (read-only; produces PASS/FAIL/BLOCKED) roles separate — see `AGENTS.md`
  §21–§22.
