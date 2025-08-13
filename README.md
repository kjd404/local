# Personal Platform

A minimal personal data platform running on a local k3d Kubernetes cluster. It provisions PostgreSQL, Metabase, and a Spring Boot ingest-service that normalizes CSV bank statements into Postgres.

## Prerequisites
- Docker
- k3d
- kubectl
- Helm
- Tilt
- age + SOPS
- Java 21 + Gradle
- buf (installed via `make deps`)

## Quickstart
```bash
make cluster-up        # create k3d cluster
make deps              # install helm repos, buf, etc.
make install-core      # install Postgres + Metabase
make build-app         # build ingest-service jar and container
make deploy            # deploy ingest-service and CronJob
```

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
