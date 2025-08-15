# Personal Platform

A minimal personal data platform running on a local k3d Kubernetes cluster. It provisions PostgreSQL and a Spring Boot ingest-service that normalizes CSV bank statements into Postgres. Metabase runs as a standalone Docker container.

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
make deps              # install Helm repo (Bitnami) and buf
make install-core      # install Postgres
make build-app         # build ingest-service jar and container
make deploy            # deploy ingest-service and CronJob
make tilt              # start Tilt for live updates
```

`make cluster-up` creates a local registry on port `5001` by default. Override with `REGISTRY_PORT` if needed.

Start Metabase in a separate terminal:

```bash
kubectl port-forward svc/platform-postgresql 5432:5432 -n personal &
docker run -d -p 8080:3000 --name metabase \
  -e MB_DB_TYPE=postgres \
  -e MB_DB_DBNAME=personal \
  -e MB_DB_PORT=5432 \
  -e MB_DB_USER=user \
  -e MB_DB_PASS=changeme \
  -e MB_DB_HOST=host.docker.internal \
  metabase/metabase
```

Drop CSV files into `storage/incoming/`. The CronJob scans every 10 minutes and moves processed files to `storage/processed/`.

To iterate on the ingest-service:
```bash
make tilt
```

### Metabase
With the container running, open <http://localhost:8080> and complete the Metabase setup wizard.

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
