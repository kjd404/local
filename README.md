# Personal Platform

A minimal personal data platform running on a local k3d Kubernetes cluster. It deploys Spring Boot services like ingest-service and teller-poller to normalize CSV bank statements into a PostgreSQL database you manage.

## Design Guidelines

This project emphasizes object-oriented design with dependency injection and composition to keep classes small, cohesive, and immutable, drawing from Mark Seemann's *Dependency Injection* and Yegor Bugayenko's *Elegant Objects*. Development follows test-driven development with a focus on integration tests and dedicated test databases.

## Prerequisites
- Docker (https://docs.docker.com/get-docker/)
- k3d (e.g., `brew install k3d` or `curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash`)
- kubectl (e.g., `brew install kubectl` or https://kubernetes.io/docs/tasks/tools/)
- Helm (e.g., `brew install helm` or https://helm.sh/docs/intro/install/)
- Tilt (e.g., `brew install tilt` or https://docs.tilt.dev/install.html)
- Java 21 (JDK) + Gradle (e.g., `brew install openjdk@21 gradle` or https://adoptium.net/)
- buf (installed via `make deps`)

## Quickstart
```bash
make cluster-up        # create k3d cluster
make deps              # install Helm repo (Bitnami) and buf
make install-core      # create namespace
cp .env-sample .env    # copy sample env
# edit .env with DB credentials and optional Teller settings
source scripts/export-env.sh
make build-app         # build ingest-service and teller-poller jars and containers
make deploy            # deploy ingest-service and CronJob
make tilt              # start Tilt for live updates
```

`make cluster-up` creates a local registry on port `5001` by default. Override with `REGISTRY_PORT` if needed.

### Tilt UI

Run `make tilt` and open [http://localhost:10350](http://localhost:10350). The UI shows:
- **ingest-service** – Spring Boot app port-forwarded to `localhost:8080`.
- **ingest-cron** – CronJob that scans `storage/incoming/` for files.

Tilt rebuilds the ingest-service image and applies Kubernetes updates as source files change.

## External Database

The platform expects an existing PostgreSQL instance. Provision a database and user that the cluster can reach, then set the connection details in `.env` and run `source scripts/export-env.sh` before deploying so `make deploy` picks them up automatically.

## Data Ingestion

### CSV conventions

- Include `account_id` and `card_no` columns when available.
- When those columns are missing, name files as `<source>-<external_id>-*.csv`.
- Defaults for `account_id` and `source` may also be provided via CLI flags or API parameters.

1. Copy CSV files into `storage/incoming/`.
2. Wait for the `ingest-cron` CronJob (runs every 10 minutes) or trigger it manually:
   ```bash
   kubectl create job --from=cronjob/ingest-cron ingest-cron-manual -n personal
   ```
3. Processed files move to `storage/processed/` and records are loaded into Postgres.


## Operations
- `kubectl logs job/<name> -n personal` to view CronJob runs.
- Failed files are moved to `storage/processed/` for inspection.

## Secrets
Secrets like database credentials and Teller API tokens live in a local `.env` file. Start from `.env-sample`, populate the values, and run `source scripts/export-env.sh` before building or deploying. The `.env` file is git-ignored—never commit real secrets.

## Cleanup
```bash
make cluster-down
```
