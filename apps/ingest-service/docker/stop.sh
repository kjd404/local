#!/usr/bin/env bash
set -euo pipefail

# Stop and remove the ingest-service Docker container if present.
# Usage: bazel run //apps/ingest-service/docker:stop_container -- [--name=name]

CONTAINER_NAME="${CONTAINER_NAME:-ingest-service}"
if [[ $# -gt 0 ]]; then
  case "$1" in
    --name=*) CONTAINER_NAME="${1#--name=}"; shift;;
  esac
fi

if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "Stopping container: ${CONTAINER_NAME}" >&2
  docker rm -f "$CONTAINER_NAME" >/dev/null
  echo "Stopped." >&2
else
  echo "No container named ${CONTAINER_NAME} to stop." >&2
fi

