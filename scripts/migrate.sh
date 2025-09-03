#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load environment variables from .env if present
# shellcheck source=./export-env.sh
source "$SCRIPT_DIR/export-env.sh"

docker run --rm \
  -v "$ROOT_DIR/ops/sql":/flyway/sql \
  flyway/flyway \
  -url="$DB_URL" \
  -user="$DB_USER" \
  -password="$DB_PASSWORD" \
  migrate
