# Agents & Workflow

This repo uses a lightweight, role-based workflow to keep changes coherent and safe. Agents (or humans acting in these roles) should follow the responsibilities and handoffs below.

## Roles

### 1) Planner
- Clarifies scope into concrete tasks and acceptance criteria.
- Works from the approved plan file under `.agent/plans/`, updating checkboxes and notes as work lands.
- Maintains accessible plan artifacts (e.g., `.agent/plans/` or designated trackers) with current tasks.
- Gatekeeper for architectural changes.

**Checklist**
- [ ] Each task has a crisp “done” definition.
- [ ] No breaking change merges without a migration plan.

### 2) App Engineers
- Build and maintain services under `apps/<service>` (polyglot; e.g., Java with Dagger, Python CLIs).
- Keep each app self‑contained with `README.md` and `AGENTS.md` in its root.
- Ensure idempotent behavior and clear error handling; add tests and sample data.

**Checklist**
- [ ] App builds with Bazel and runs via `bazel run` wrappers.
- [ ] Tests colocated with the code; cover edge cases (encodings, dates, negatives).
- [ ] Docker image builds for arm64/amd64 (if applicable).
- [ ] App README lists env vars, run commands, and troubleshooting.
- [ ] Relevant integration runbooks in `runbooks/` are current and executed for release validation.

### 3) Data Engineer
- Owns schema evolution in `ops/sql/<service>` and JOOQ regeneration where used.
- Defines canonical columns and indexes per service; coordinates cross‑service changes.

**Checklist**
- [ ] Flyway migrations are backward‑compatible or include migration notes.
- [ ] Use `flyway_migration` macro per service with env var prefixes (e.g., `SERVICEA_DB_URL`).
- [ ] jOOQ codegen updated after schema changes (where applicable).
- [ ] Constraints/indexes maintain idempotence and performance.


## Handoffs
- **Planner → App/Data:** tasks created with acceptance tests and necessary context.
- **Operator feedback → Planner:** Reliability issues become tasks.

## Guardrails
- Keep secrets only in local, git-ignored `.env` files; never commit them to the repository.
- House agent collateral under `.agent/`—prompts in `.agent/prompts/`, plans in `.agent/plans/`, and research outputs (git-ignored) under `.agent/reports/`; keep plan state synchronized so handoffs stay unblocked.
- Changes that affect storage or schema require a migration plan in PR description.
- Bazel is the blessed entry point; helper scripts should be idempotent and invokable via `bazel run` wrappers.
- Python is first‑class for productivity; use `bazel run //:venv` for a repo-local venv and `bazel run //:lock` to update pinned deps shared with Bazel.
- Tests remain colocated by default. The curated `bazel test //tests:repo_suite` target is the sanctioned repo-wide aggregator; introduce additional root-level suites only after planner review.

## Object-Oriented Design

Prefer dependency injection and object composition as described in
*Dependency Injection: Principles, Practices, and Patterns* by Mark Seemann and Steven van Deursen.
Follow the guidelines in Yegor Bugayenko's *Elegant Objects* for small, cohesive classes
and constructor-based immutability.

## Getting Started (human or agent)
1. `docker compose up -d` to start Postgres.
2. `cp .env-sample .env` and set `DB_URL`, `DB_USER`, and `DB_PASSWORD`.
3. Python tooling (optional):
   - `bazel run //:venv` to create `.venv` and install pinned deps from `requirements.lock`.
   - `bazel run //:lock` to update `requirements.lock` from `requirements.in`.
4. Build and run an app (example: ingest-service):
   - `bazel build //apps/ingest-service:ingest_app`
   - `bazel run //apps/ingest-service:ingest_app -- --mode=scan`
5. Drop a sample CSV (e.g., `co1828-example.csv` or `ch1234-example.csv` from `apps/ingest-service/src/test/resources/examples`) into `storage/incoming/`, or run the app locally pointing at the database.

## Testing & PRs
- Tests are colocated with their code; run targeted suites (e.g., `bazel test //apps/ingest-service:ingest_tests`) and lean on the curated repo aggregator `bazel test //tests:repo_suite` when sweeping cross-service changes.
- Build the ingest app: `bazel build //apps/ingest-service:ingest_app`.
- Build the Docker image: `bazel run //apps/ingest-service/docker:build_image`.
- **PR Checklist**
  - [ ] All relevant tests pass (targeted suites plus `bazel test //tests:repo_suite` for repo-wide refactors).
  - [ ] Migration plan noted for storage or schema changes (including env prefixes if multi-service).
  - [ ] PR description lists the commands executed.

## Future Extensions
- Additional services onboarded into `apps/` (Rust/Java/Python as needed).
- gRPC/HTTP endpoints for cross‑service messaging.
- Shared libraries under `libs/<lang>` where reuse is clear.
- E2E suite under `e2e/` once multi‑service flows exist.

## Ongoing Design Tasks
- Colocate tests with code; reserve `bazel test //tests:repo_suite` for cross-service verification.
- Adopt per‑service Flyway targets (`flyway_migration`) and document env prefixes per app.
- Keep each app’s `README.md` and `AGENTS.md` current (build/run/env/tests).
- Maintain jOOQ codegen targets in Bazel and refresh after schema changes.
- Keep Python hermetic toolchain current; `requirements.lock` remains the single source of truth for packages.
- Incrementally extract shared logic into `libs/<lang>` when justified by reuse.

## Monorepo Conventions
- Structure: `apps/<service>`, `libs/<lang>/...`, `ops/sql/<service>`, `tools/<lang>/...`.
- Tests: colocated with the code; `//tests:repo_suite` is the sanctioned repo-wide aggregator when needed.
- Database migrations: use `flyway_migration` macro (`//tools/sql:flyway.bzl`) to define `:db_migrate` per service; prefer `<SERVICE>_DB_*` env vars.
- Bazel: bzlmod at repo root; JVM deps via `rules_jvm_external`. Python packages are pinned in `requirements.lock` and consumed by both Bazel and the venv.
