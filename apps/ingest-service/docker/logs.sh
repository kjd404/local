#!/usr/bin/env bash
set -euo pipefail

# Tail logs from the running ingest-service container.
# Usage: bazel run //apps/ingest-service/docker:logs -- [name]

NAME="${1:-${CONTAINER_NAME:-ingest-service}}"
exec docker logs -f "$NAME"
