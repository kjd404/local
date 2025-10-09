#!/usr/bin/env bash
set -euo pipefail

# Bazel-friendly wrapper to run Flyway migrations using Docker.
# Reads DB_URL, DB_USER, DB_PASSWORD from the environment or from .env in the workspace root.

WORKSPACE_DIR="${BUILD_WORKSPACE_DIRECTORY:-}"
if [[ -z "$WORKSPACE_DIR" ]]; then
  echo "BUILD_WORKSPACE_DIRECTORY not set. Run via: bazel run //ops/sql:db_migrate" >&2
  exit 2
fi

# Load .env if present and DB vars not already set
if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  if [[ -f "$WORKSPACE_DIR/.env" ]]; then
    set +u
    set -a
    # shellcheck disable=SC1090
    source <(tr -d '\r' < "$WORKSPACE_DIR/.env")
    set +a
    set -u
  fi
fi

: "${DB_URL:?DB_URL is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

# If DB_URL points to localhost, rewrite for Docker network access
DB_URL=${DB_URL/localhost/host.docker.internal}
DB_URL=${DB_URL/127.0.0.1/host.docker.internal}

# Copy top-level SQL files into a temporary directory so Flyway sees only the
# canonical ingest migrations (subdirectories house service-specific scripts).
SQL_TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$SQL_TMP_DIR"' EXIT
find "$WORKSPACE_DIR/ops/sql" -maxdepth 1 -type f -name '*.sql' -exec cp {} "$SQL_TMP_DIR" \;

if [[ "$DB_URL" != jdbc:* ]]; then
  DB_URL="jdbc:$DB_URL"
fi

docker run --rm \
  -v "$SQL_TMP_DIR":/flyway/sql \
  flyway/flyway \
  -url="$DB_URL" \
  -user="$DB_USER" \
  -password="$DB_PASSWORD" \
  migrate
RC=$?
exit $RC
