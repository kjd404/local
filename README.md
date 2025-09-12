# Personal Platform

A minimal personal data platform that normalizes CSV bank statements into a PostgreSQL database you control.

## Design Guidelines

This project emphasizes object-oriented design with dependency injection and composition to keep classes small, cohesive, and immutable, drawing from Mark Seemann's *Dependency Injection* and Yegor Bugayenko's *Elegant Objects*. Development follows test-driven development with a focus on integration tests and dedicated test databases.

## Prerequisites
- Docker (https://docs.docker.com/get-docker/)
- Bazel or Bazelisk (https://bazel.build/ or https://github.com/bazelbuild/bazelisk)

## Quickstart

```bash
docker compose up -d       # start Postgres on localhost:5432 with a persistent volume
cp .env-sample .env        # copy sample env and edit DB credentials

# Build and run with Bazel
bazel build //apps/ingest-service:ingest_app
bazel run //apps/ingest-service:ingest_app -- --mode=scan

# Database migrations
bazel run //ops/sql:db_migrate                                         # apply Flyway migrations

# Build/run via Docker (wrappers)
bazel run //apps/ingest-service/docker:build_image -- --tag=ingest:latest      # docker build
bazel run //apps/ingest-service/docker:run_container -- --image=ingest:latest  # docker run (detached); no-op if already running
bazel run //apps/ingest-service/docker:run_container -- --image=ingest:latest --host-port=8081   # specify host port
bazel run //apps/ingest-service/docker:run_container -- --image=ingest:latest --auto-port        # auto-pick next free port
bazel run //apps/ingest-service/docker:run_container -- --image=ingest:latest --restart  # force restart
bazel run //apps/ingest-service/docker:run_container -- --image=ingest:latest --attach   # attach/logs if running
bazel run //apps/ingest-service/docker:stop_container -- --name=ingest-service           # stop container
bazel run //apps/ingest-service/docker:status -- --name=ingest-service                   # status + ports
```

Alternatively, run the CLI directly:

```bash
# Using Bazel (no local JDK required):
bazel run //apps/ingest-service:ingest_app -- --mode=scan
bazel run //apps/ingest-service:ingest_app -- --file=/path/to/ch1234-example.csv

# Or run a staged jar
java -jar apps/ingest-service/app.jar --mode=scan
java -jar apps/ingest-service/app.jar --file=/path/to/ch1234-example.csv
```

Stop the database with `docker compose down` when finished.

## Monorepo Layout

- `apps/<service>`: application code with per-app `README.md` and `AGENTS.md`.
- `libs/<lang>`: shared libraries by language (add as needed).
- `ops/sql/<service>`: Flyway migrations per service; use the shared macro `flyway_migration`.
- `tools/<lang>`: developer tooling (e.g., `tools/python`, `tools/sql`).
- `e2e/` (optional): cross-service tests if/when needed.

## Python Tooling

- Create/update a repo-local virtual environment using Bazel’s hermetic Python: `bazel run //:venv`
- Activate it: `source .venv/bin/activate` (IDE/LSP: point to `.venv/bin/python`).
- Dependencies are pinned in a single lockfile `requirements.lock` and used by both:
  - Local venv (`pip install -r requirements.lock` in the script).
  - Bazel via `rules_python` (`pip.parse(requirements_lock = "//:requirements.lock")`).

- Generate/refresh the lockfile from `requirements.in` with: `bazel run //:lock`

This keeps interpreter and package resolution consistent across local development and Bazel builds. To change dependencies, edit `requirements.in` and re-lock to `requirements.lock` with your preferred tool (e.g., uv or pip-tools), then rerun `bazel run //:venv`.

## Formatter Binaries

The pre-commit hooks include system-formatters. Install these locally so hooks can run without errors:

- buildifier: formats Bazel/Starlark files (BUILD/WORKSPACE/MODULE.bazel/*.bzl)
  - macOS (Homebrew): `brew install buildifier`
  - Debian/Ubuntu: `sudo apt-get install buildifier` (or install from Bazelisk releases)
  - Else: download from https://github.com/bazelbuild/buildtools/releases

- pg_format: formats `.sql` files
  - macOS (Homebrew): `brew install pgformatter`
  - Ubuntu/Debian: `sudo apt-get install pgformatter`
  - Fedora: `sudo dnf install pgformatter`

- google-java-format: formats `.java` and removes unused imports
  - macOS (Homebrew): `brew install google-java-format`
  - Other platforms: download the release JAR and expose a `google-java-format` command on your PATH, e.g. `alias google-java-format='java -jar /path/to/google-java-format-<ver>-all-deps.jar'`
  - Requires a local Java runtime (JRE/JDK)

To format everything at once (BUILD/Starlark, SQL, Java): `bazel run //:format`.
This aggregates buildifier, pg_format, and runs the local google-java-format
pre-commit hook for all Java files.

If you prefer not to install system binaries, you can disable or adjust the corresponding hook in `.pre-commit-config.yaml`.

## Git Blame (Ignore Formatting Commits)

The file `.git-blame-ignore-revs` lists formatting-only commits so they don't
pollute `git blame` output.

- Enable for this repository:
  - `git config blame.ignoreRevsFile .git-blame-ignore-revs`
- Optional: set a global ignore file and manage it centrally (advanced):
  - `git config --global blame.ignoreRevsFile ~/.config/git/ignore-revs`
  - Append commit hashes from this repo's `.git-blame-ignore-revs` into that file.

## SQL Per-Service Migrations

- Reusable macro: `//tools/sql:flyway.bzl` exposes `flyway_migration(name, sql_dir, env_prefix)`.
- Example template: `ops/sql/service-template` with `:db_migrate`.
- Provide DB vars via `.env` using service prefixes, e.g. `SERVICE_TEMPLATE_DB_URL`.

## Package Structure

The ingest-service Java code is organized into cohesive packages:

- `org.artificers.ingest.app`: CLI entrypoint and app wiring
- `org.artificers.ingest.csv`: CSV parsing and mapping utilities
- `org.artificers.ingest.model`: Immutable domain records and interfaces
- `org.artificers.ingest.service`: Core services (ingest, repository, watch, resolvers)
- `org.artificers.ingest.di`: Dagger modules and component
- `org.artificers.ingest.validation`: Transaction validation contracts and basics
- `org.artificers.ingest.error`: Domain exceptions

### Environment Variables

The CLI reads the following variables (via the shell or `.env`):

- `DB_URL` – JDBC URL of the PostgreSQL database.
- `DB_USER` – database username.
- `DB_PASSWORD` – database password.
- `INGEST_DIR` – optional directory to scan for CSV files (defaults to `storage/incoming`).

## Schema

All transactions land in a single `transactions` table.  In addition to
canonical columns like `occurred_at`, `amount_cents`, and `merchant`, the
table includes a `raw_json` column that preserves every original CSV field
as JSON for later auditing or enrichment.  A convenience view
`transactions_view` joins `transactions` with `accounts` to expose the
institution code alongside each row.

## Data Ingestion

### CSV conventions

- Name files using the account shorthand `<institution><last4>-*.csv`, e.g. `co1828-2025-04.csv`.
- The shorthand is used as the internal account identifier; CSVs need not include account or source columns.

1. Copy CSV files into `storage/incoming/`.
2. Run the CLI or service to ingest them (it watches `storage/incoming/` by default, configurable via `--input` or `INGEST_DIR`).
3. Processed files move to `storage/incoming/processed/` (failures to `storage/incoming/error/`) and records are loaded into Postgres.

## Bazel Utilities

- `//apps/ingest-service:db_validate`: prints row counts, totals, per-account counts, and duplicate checks.
- `//ops/sql:db_migrate`: runs core Flyway migrations via Docker using `.env`.
- `//ops/sql/service-template:db_migrate`: example per-service migration target via the shared macro.
- `//apps/ingest-service/docker:build_image`: builds the Docker image via Docker CLI using the Bazel deploy jar.
- `//apps/ingest-service/docker:run_container`: runs the Docker image locally (reads DB vars from `.env`, detached by default).
- `//apps/ingest-service/docker:logs`: tails logs from the running container.

### Mapping files

CSV columns are mapped to the canonical schema via per-institution mapping
files.  These YAML or JSON files live under
`~/.config/ingest/mappings/` (override with `INGEST_CONFIG_DIR`) and are
named `<institution>.yaml` or `<institution>.json`.  Each entry maps a
normalized header to a target field and type:

```yaml
institution: xx
fields:
  transaction_date:
    target: OCCURRED_AT
    type: timestamp
  amount:
    target: AMOUNT_CENTS
    type: currency
  description:
    target: MERCHANT
    type: string
```

The ingest service watches this directory and hot-reloads mappings when
files change.

### Creating accounts

Run `./scripts/new-account` from the repository root to add an account and
generate a mapping template.  The script prompts for institution code,
last-four external ID, display name, and optional currency, then writes
`~/.config/ingest/mappings/<institution>.yaml`.  Pass `--force` to
overwrite an existing mapping file.

### Adding new institutions

To onboard another institution, run `./scripts/new-account`, edit the
generated mapping file to match that institution's CSV headers, and place
its statements in `storage/incoming/`.  No code changes or rebuilds are
required; the service will pick up the new mapping automatically.

### Sample CSVs

Example statements demonstrating UTF-8 merchants and positive/negative amounts live under `apps/ingest-service/src/test/resources/examples/`:

- `co1828-example.csv` – Capital One Venture X
- `ch1234-example.csv` – Chase Freedom

## Metabase

Metabase users should query the `transactions_view` view for a unified
list of transactions across all institutions.  The view simply joins the
`transactions` table with `accounts` to expose the `institution` column
alongside each transaction.  Because it's a regular SQL view, newly
ingested data is immediately visible. If performance becomes an issue,
consider converting it to a materialized view and refresh it after each
ingest:

```sql
REFRESH MATERIALIZED VIEW transactions_view;
```

## Secrets

Secrets like database credentials live in a local `.env` file. Start from `.env-sample`, populate the values, and the build/run tooling will read them automatically. If you need the variables in your shell for ad-hoc commands, run `source scripts/export-env.sh`. The `.env` file is git-ignored—never commit real secrets.

## Cleanup

```bash
docker compose down
```
