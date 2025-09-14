#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-athena-bot}"
exec docker logs -f "$CONTAINER_NAME"
