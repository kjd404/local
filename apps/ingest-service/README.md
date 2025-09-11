# Ingest Service

CLI service that normalizes CSV/XLSX bank statements into a PostgreSQL
schema you control. It maps institution‑specific columns into a canonical
set, preserves the full raw record as JSON, and enforces idempotent
upserts using stable hashing.

## Build & Run
- Build: `bazel build //apps/ingest-service:ingest_app`
- Scan for new CSVs: `bazel run //apps/ingest-service:ingest_app -- --mode=scan`
- Process a single file: `bazel run //apps/ingest-service:ingest_app -- --file=storage/incoming/ch1234-example.csv`

## Environment
- `DB_URL`, `DB_USER`, `DB_PASSWORD` (read from shell or repo‑local `.env`).
- `INGEST_DIR` optional (defaults to `storage/incoming`).

## Migrations
Core schema migrations live under `ops/sql/` and can be applied via:
- `bazel run //ops/sql:db_migrate`

## Docker Helpers
- Build image: `bazel run //apps/ingest-service/docker:build_image -- --tag=ingest:latest`
- Run container: `bazel run //apps/ingest-service/docker:run_container -- --image=ingest:latest`
- Logs: `bazel run //apps/ingest-service/docker:logs`

## Tests
- `bazel test //apps/ingest-service:ingest_tests`
- Tests are colocated with the code (`src/test/java`) and use JUnit 5.

## Notes
- Dependencies are managed via bzlmod (see repo root `MODULE.bazel`).
- For Python productivity tools, create a venv with `bazel run //:venv`
  and point your IDE to `.venv/bin/python`.
