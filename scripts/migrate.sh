#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<USAGE
Usage: $(basename "$0")
Runs Flyway migrations in a Docker container using variables from .env.
Requires: docker, .env with DB_URL, DB_USER, DB_PASSWORD.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load environment variables from .env if present
# shellcheck source=./export-env.sh
source "$SCRIPT_DIR/export-env.sh"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found in PATH" >&2
  exit 127
fi

: "${DB_URL:?DB_URL is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

# Ensure DB_URL uses JDBC format for Flyway
if [[ "$DB_URL" != jdbc:* ]]; then
  DB_URL="jdbc:$DB_URL"
fi

exec docker run --rm \
  -v "$ROOT_DIR/ops/sql":/flyway/sql \
  flyway/flyway \
  -url="$DB_URL" \
  -user="$DB_USER" \
  -password="$DB_PASSWORD" \
  migrate
