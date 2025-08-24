# Agents & Workflow

This repo uses a lightweight, role-based workflow to keep changes coherent and safe. Agents (or humans acting in these roles) should follow the responsibilities and handoffs below.

## Roles

### 1) Planner
- Clarifies scope into concrete tasks and acceptance criteria.
- Maintains the task list in README (or issues).
- Gatekeeper for architectural changes.

**Checklist**
- [ ] Each task has a crisp “done” definition.
- [ ] No breaking change merges without a migration plan.

### 2) Infra Engineer
- Owns k3d/Helm/Kustomize/Tilt wiring, namespaces, secrets, PVCs.
- Adds/updates charts under `charts/platform` and overlays in `deploy/local`.
- Keeps Make targets working across macOS (arm64) and x86_64.

**Checklist**
- [ ] `make cluster-up/down` works.
- [ ] Postgres charts upgraded without breaking data.
- [ ] hostPath mounts documented and not absolute in templates.

### 3) App Engineer (Ingest)
- Builds `apps/ingest-service` (Spring Boot, JOOQ, Flyway, CSV/XLSX parsing).
- Ensures idempotent upserts with stable hashing.
- Adds tests and sample data.

**Checklist**
- [ ] CLI `--mode=scan --input=/incoming` ingests sample CSV → Postgres.
- [ ] Unit test covers mapper edge cases (dates, negative amounts, UTF-8).
- [ ] Docker image builds for arm64/amd64 (if feasible).

### 4) Data Engineer
- Owns schema evolution in `ops/sql/` and JOOQ regeneration.
- Defines canonical columns and indexes.

**Checklist**
- [ ] Flyway migrations are backward-compatible or include migration notes.
- [ ] JOOQ codegen updated after schema changes.
- [ ] Constraints/indexes keep ingest idempotent and performant.


### 5) Operator (Runtime)
- Monitors CronJob logs, verifies successful runs, and triages failures.
- Adds a `failed/` folder workflow for problematic files.

**Checklist**
- [ ] CronJob success/failure visible via `kubectl logs` guidance.
- [ ] Processed and failed files moved to correct folders.
- [ ] Simple runbook in README → “Operations”.

## Handoffs
- **Planner → Infra:** cluster/charts tasks created with acceptance tests.
- **Infra → App/Data:** DB connection info via `.env` or environment variables; service DNS documented.
- **Operator feedback → Planner:** Reliability issues become tasks.

## Guardrails
- Keep secrets only in local, git-ignored `.env` files; never commit them to the repository.
- Changes that affect storage or schema require a migration plan in PR description.
- Prefer Helm values and overlays over editing templates directly.
- Keep Make/Tilt the blessed entry points; scripts should be idempotent.

## Object-Oriented Design

Prefer dependency injection and object composition as described in
*Dependency Injection: Principles, Practices, and Patterns* by Mark Seemann and Steven van Deursen.
Follow the guidelines in Yegor Bugayenko's *Elegant Objects* for small, cohesive classes
and constructor-based immutability.

## Getting Started (human or agent)
1. `make cluster-up && make deps && make install-core`
2. Set `DB_URL`, `DB_USER`, and `DB_PASSWORD` (plus optional `TELLER_TOKENS`, `TELLER_CERT_FILE`, `TELLER_KEY_FILE`).
3. `make build-app && make deploy`
4. Drop a sample CSV into `storage/incoming/`, or run the app locally pointing at cluster DB.
5. Iterate with `tilt up` for live dev.

## Testing & PRs
- Run unit tests with `cd apps/ingest-service && ./gradlew test`.
- Build the app and image with `make build-app`.
- **PR Checklist**
  - [ ] Tests pass and `make build-app` succeeds.
  - [ ] Migration plan noted for storage or schema changes.
  - [ ] PR description lists the commands executed.

## Future Extensions
- gRPC endpoints for cross-service messaging using the existing `buf` workspace.
- Nightly `pg_dump` CronJob to a backup hostPath.
- NetworkPolicies tightening & non-root containers.
- Additional services (budgeting rules, receipt OCR, alerts).

## Ongoing Design Tasks
- Audit existing services for alignment with the Object-Oriented Design guardrails and schedule refactors where needed.
- Introduce dependency injection and composition patterns across modules lacking them.
- Record design decisions and remaining work here. After completing any task, update this section with progress and new objectives.

