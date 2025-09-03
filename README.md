# Personal Platform

A minimal personal data platform that normalizes CSV bank statements into a PostgreSQL database you control.

## Design Guidelines

This project emphasizes object-oriented design with dependency injection and composition to keep classes small, cohesive, and immutable, drawing from Mark Seemann's *Dependency Injection* and Yegor Bugayenko's *Elegant Objects*. Development follows test-driven development with a focus on integration tests and dedicated test databases.

## Prerequisites
- Docker (https://docs.docker.com/get-docker/)
- Java 21 (JDK) + Gradle (e.g., `brew install openjdk@21 gradle` or https://adoptium.net/)
- buf (installed via `make deps`)

## Quickstart

```bash
docker compose up -d    # start Postgres on localhost:5432 with a persistent volume
cp .env-sample .env     # copy sample env
# edit .env with DB credentials
make build-app          # build ingest-service jar
make docker-build       # build Docker image
make docker-run DB_URL=jdbc:postgresql://localhost:5432/ingest DB_USER=ingest DB_PASSWORD=ingest
```

Alternatively, run the CLI directly:

```bash
./apps/ingest-service/gradlew -p apps/ingest-service bootRun --args='--mode=scan'  # defaults to storage/incoming
```

Stop the database with `docker compose down` when finished.

## Data Ingestion

### CSV conventions

- Name files using the account shorthand `<institution><last4>-*.csv`, e.g. `co1828-2025-04.csv`.
- The shorthand is used as the internal account identifier; CSVs need not include account or source columns.

1. Copy CSV files into `storage/incoming/`.
2. Run the CLI or service to ingest them (it watches `storage/incoming/` by default, configurable via `--input` or `INGEST_DIR`).
3. Processed files move to `storage/processed/` and records are loaded into Postgres.

### Sample CSVs

Example statements demonstrating UTF-8 merchants and positive/negative amounts live under `apps/ingest-service/src/test/resources/com/example/ingest/`:

- `co1828-example.csv` – Capital One Venture X
- `ch1234-example.csv` – Chase Freedom

## Secrets

Secrets like database credentials live in a local `.env` file. Start from `.env-sample`, populate the values, and the build/run tooling will read them automatically. If you need the variables in your shell for ad-hoc commands, run `source scripts/export-env.sh`. The `.env` file is git-ignored—never commit real secrets.

## Cleanup

```bash
docker compose down
```

