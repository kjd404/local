# Personal Platform

A minimal personal data platform that normalizes CSV bank statements into a PostgreSQL database you control.

## Design Guidelines

This project emphasizes object-oriented design with dependency injection and composition to keep classes small, cohesive, and immutable, drawing from Mark Seemann's *Dependency Injection* and Yegor Bugayenko's *Elegant Objects*. Development follows test-driven development with a focus on integration tests and dedicated test databases.

## Prerequisites
- Docker (https://docs.docker.com/get-docker/)
- Bazel or Bazelisk (https://bazel.build/ or https://github.com/bazelbuild/bazelisk)
- buf (for proto codegen; install from https://buf.build/)

## Quickstart

```bash
docker compose up -d    # start Postgres on localhost:5432 with a persistent volume
cp .env-sample .env     # copy sample env
# edit .env with DB credentials
make build-app          # build ingest-service jar with Bazel
make docker-build       # build Docker image
make docker-run DB_URL=jdbc:postgresql://localhost:5432/ingest DB_USER=ingest DB_PASSWORD=ingest
```

Alternatively, run the CLI directly:

```bash
# Using Bazel (no local JDK required):
bazel run //apps/ingest-service:ingest_app -- --mode=scan
bazel run //apps/ingest-service:ingest_app -- --file=/path/to/ch1234-example.csv

# Or run the jar produced by make build-app
java -jar apps/ingest-service/app.jar --mode=scan
java -jar apps/ingest-service/app.jar --file=/path/to/ch1234-example.csv
```

Stop the database with `docker compose down` when finished.

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

## Scripts

- `scripts/new-account`: runs the New Account CLI (prompts for info; writes a mapping template). Use `--force` to overwrite.
- `scripts/migrate.sh`: runs Flyway migrations in Docker using `.env` for DB credentials.
- `scripts/app-logs.sh [container]`: tails logs from the running Docker container (default `ingest-service`).

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
