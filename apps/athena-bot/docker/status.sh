#!/usr/bin/env bash
set -euo pipefail

NAME="${1:-${CONTAINER_NAME:-athena-bot}}"
docker ps --filter "name=^/${NAME}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
