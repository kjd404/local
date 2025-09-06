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

### 2) App Engineer (Ingest)
- Builds `apps/ingest-service` (Java + Dagger + Picocli, jOOQ, Flyway, CSV/XLSX parsing).
- Ensures idempotent upserts with stable hashing.
- Adds tests and sample data.

**Checklist**
- [ ] CLI `--mode=scan` ingests sample CSV from `storage/incoming` → Postgres.
- [ ] Unit test covers mapper edge cases (dates, negative amounts, UTF-8).
- [ ] Docker image builds for arm64/amd64 (if feasible).

### 3) Data Engineer
- Owns schema evolution in `ops/sql/` and JOOQ regeneration.
- Defines canonical columns and indexes.

**Checklist**
- [ ] Flyway migrations are backward-compatible or include migration notes.
- [ ] JOOQ codegen updated after schema changes.
- [ ] Constraints/indexes keep ingest idempotent and performant.


## Handoffs
- **Planner → App/Data:** tasks created with acceptance tests and necessary context.
- **Operator feedback → Planner:** Reliability issues become tasks.

## Guardrails
- Keep secrets only in local, git-ignored `.env` files; never commit them to the repository.
- Changes that affect storage or schema require a migration plan in PR description.
- Keep `make` the blessed entry point; scripts should be idempotent.

## Object-Oriented Design

Prefer dependency injection and object composition as described in
*Dependency Injection: Principles, Practices, and Patterns* by Mark Seemann and Steven van Deursen.
Follow the guidelines in Yegor Bugayenko's *Elegant Objects* for small, cohesive classes
and constructor-based immutability.

## Getting Started (human or agent)
1. `docker compose up -d` to start Postgres.
2. `cp .env-sample .env` and set `DB_URL`, `DB_USER`, and `DB_PASSWORD`.
3. Build with Bazel:
   - `bazel build //apps/ingest-service:ingest_app`
   - Run: `bazel run //apps/ingest-service:ingest_app -- --mode=scan`
4. Drop a sample CSV (e.g., `co1828-example.csv` or `ch1234-example.csv` from `apps/ingest-service/src/test/resources/examples`) into `storage/incoming/`, or run the app locally pointing at the database.

## Testing & PRs
- Build the app and image with `make build-app`.
- Bazel is enabled at the repo root via bzlmod and currently builds only `apps/ingest-service`. Bazel-based tests will be added in a follow-up.
- **PR Checklist**
  - [ ] Tests pass and `make build-app` succeeds.
  - [ ] Migration plan noted for storage or schema changes.
  - [ ] PR description lists the commands executed.

## Future Extensions
- gRPC endpoints for cross-service messaging using the existing `buf` workspace.
- Additional services (budgeting rules, receipt OCR, alerts).

## Ongoing Design Tasks
- Audit existing services for alignment with the Object-Oriented Design guardrails and schedule refactors where needed.
- Introduce dependency injection and composition patterns across modules lacking them.
- Record design decisions and remaining work here. After completing any task, update this section with progress and new objectives.

## Bazel Migration Notes
- Scope: Bazel is configured at the repo root (bzlmod) and, for now, only indexes/builds `apps/ingest-service`. Other paths have no BUILD files and are ignored by Bazel.
- Java toolchain: `.bazelrc` sets Java 17 with the remote toolchain.
- Dependencies: managed with `rules_jvm_external` via `MODULE.bazel`.
- Next steps: add Bazel tests (JUnit 5), wire jOOQ codegen as a Bazel action, and progressively onboard other services.
