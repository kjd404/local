# Personal Platform

A minimal personal data platform running on a local k3d Kubernetes cluster. It provisions PostgreSQL, Metabase, and a Spring Boot ingest-service that normalizes CSV bank statements into Postgres.

## Prerequisites
- Docker (https://docs.docker.com/get-docker/)
- k3d (e.g., `brew install k3d` or `curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash`)
- kubectl (e.g., `brew install kubectl` or https://kubernetes.io/docs/tasks/tools/)
- Helm (e.g., `brew install helm` or https://helm.sh/docs/intro/install/)
- Tilt (e.g., `brew install tilt` or https://docs.tilt.dev/install.html)
- age + SOPS (e.g., `brew install age sops` or https://github.com/mozilla/sops#installation)
- Java 21 + Gradle (e.g., `brew install openjdk@21 gradle` or https://adoptium.net/)
- buf (installed via `make deps`)

## Quickstart
```bash
make cluster-up        # create k3d cluster
make deps              # install Helm repos (Bitnami \& Metabase) and buf
make install-core      # install Postgres + Metabase
make build-app         # build ingest-service jar and container
make deploy            # deploy ingest-service and CronJob
The `deps` target adds the Bitnami and Metabase Helm repositories, using the official Metabase chart repo at https://metabase.github.io/helm-charts.

Drop CSV files into `storage/incoming/`. The CronJob scans every 10 minutes and moves processed files to `storage/processed/`.

To iterate on the ingest-service:
```bash
make tilt
```

### Metabase
Metabase is exposed via the k3d load balancer. After `cluster-up` and `install-core`, run:
```bash
kubectl get svc -n personal
```
Look for the `metabase` `EXTERNAL-IP` (usually `0.0.0.0:8080`). Open `http://localhost:8080`.

## Operations
- `kubectl logs job/<name> -n personal` to view CronJob runs.
- Failed files are moved to `storage/processed/` for inspection.

## Secrets
Secrets live in SOPS-encrypted files under `charts/platform/*.sops.yaml`.
To edit:
```bash
sops charts/platform/values.local.sops.yaml
```

## Cleanup
```bash
make cluster-down
```
