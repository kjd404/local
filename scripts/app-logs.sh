#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${1:-ingest-service}"

docker logs -f "$CONTAINER_NAME"
