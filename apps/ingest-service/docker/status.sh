#!/usr/bin/env bash
set -euo pipefail

# Show container status and port mappings.
# Usage: bazel run //apps/ingest-service/docker:status -- [--name=name]

CONTAINER_NAME="${CONTAINER_NAME:-ingest-service}"
if [[ $# -gt 0 ]]; then
  case "$1" in
    --name=*) CONTAINER_NAME="${1#--name=}"; shift;;
  esac
fi

CID=$(docker ps -aq --filter "name=^/${CONTAINER_NAME}$" || true)
if [[ -z "$CID" ]]; then
  echo "name: $CONTAINER_NAME"
  echo "state: not_found"
  exit 0
fi

RUNNING=$(docker inspect -f '{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null || echo false)
STATUS=$(docker inspect -f '{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo unknown)
IMAGE=$(docker inspect -f '{{.Config.Image}}' "$CONTAINER_NAME" 2>/dev/null || echo unknown)
PORTS=$(docker port "$CONTAINER_NAME" 2>/dev/null || true)

echo "name: $CONTAINER_NAME"
echo "container_id: $CID"
echo "state: $STATUS"
echo "running: $RUNNING"
echo "image: $IMAGE"
if [[ -n "$PORTS" ]]; then
  echo "ports:"
  while IFS= read -r line; do
    echo "  $line"
  done <<< "$PORTS"
else
  echo "ports: none"
fi
