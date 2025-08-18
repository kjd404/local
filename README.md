# Personal Platform

A minimal personal data platform running on a local k3d Kubernetes cluster. It provisions PostgreSQL and a Spring Boot ingest-service that normalizes CSV bank statements into Postgres.

## Prerequisites
- Docker (https://docs.docker.com/get-docker/)
- k3d (e.g., `brew install k3d` or `curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash`)
- kubectl (e.g., `brew install kubectl` or https://kubernetes.io/docs/tasks/tools/)
- Helm (e.g., `brew install helm` or https://helm.sh/docs/intro/install/)
- Tilt (e.g., `brew install tilt` or https://docs.tilt.dev/install.html)
- age + SOPS (e.g., `brew install age sops` or https://github.com/mozilla/sops#installation)
- Java 21 (JDK) + Gradle (e.g., `brew install openjdk@21 gradle` or https://adoptium.net/)
- buf (installed via `make deps`)

## Quickstart
```bash
make cluster-up        # create k3d cluster
make deps              # install Helm repo (Bitnami) and buf
make install-core      # install Postgres
make build-app         # build ingest-service jar and container
make deploy            # deploy ingest-service and CronJob
make tilt              # start Tilt for live updates
```

`make cluster-up` creates a local registry on port `5001` by default. Override with `REGISTRY_PORT` if needed.

### Tilt UI

Run `make tilt` and open [http://localhost:10350](http://localhost:10350). The UI shows:
- **ingest-service** – Spring Boot app port-forwarded to `localhost:8080`.
- **ingest-cron** – CronJob that scans `storage/incoming/` for files.
- **platform-postgresql** – PostgreSQL database from the Bitnami chart.

Tilt rebuilds the ingest-service image and applies Kubernetes updates as source files change.

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
Secrets live in SOPS-encrypted files under `charts/platform/*.sops.yaml`.
### Secrets with SOPS

- Generate an age key:
  ```bash
  age-keygen -o ~/.config/sops/age/keys.txt
  ```
- Store the key at `~/.config/sops/age/keys.txt`.
- Edit encrypted files:
  ```bash
  sops charts/platform/values.local.sops.yaml
  ```
- Rotate or add recipients by updating `.sops.yaml`.

`.sops.yaml` contains placeholder values—replace the example key with your own age public key. Add decrypted files (like `charts/platform/values.local.yaml`) to `.gitignore` to keep plaintext secrets out of version control.

## Cleanup
```bash
make cluster-down
```
