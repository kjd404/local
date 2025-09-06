#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $(basename "$0") [container_name]" >&2
  echo "Tails logs from a running Docker container (default: ingest-service)." >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

CONTAINER_NAME="${1:-${CONTAINER_NAME:-ingest-service}}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found in PATH" >&2
  exit 127
fi

exec docker logs -f "$CONTAINER_NAME"
