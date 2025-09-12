#!/usr/bin/env bash
set -euo pipefail

# Generic Flyway migration runner for Bazel.
# Usage (via Bazel macro):
#   bazel run //ops/sql/<service>:db_migrate
#
# Flags:
#   --sql-dir=<path>      Path to migrations dir relative to workspace root.
#   --env-prefix=<PREFIX> Optional env var prefix (PREFIX_DB_URL, etc.).

SQL_DIR=""
ENV_PREFIX=""
for arg in "$@"; do
  case "$arg" in
    --sql-dir=*) SQL_DIR="${arg#*=}" ;;
    --env-prefix=*) ENV_PREFIX="${arg#*=}" ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

if [[ -z "$SQL_DIR" ]]; then
  echo "--sql-dir is required" >&2
  exit 2
fi

WORKSPACE_DIR="${BUILD_WORKSPACE_DIRECTORY:-}"
if [[ -z "$WORKSPACE_DIR" ]]; then
  echo "BUILD_WORKSPACE_DIRECTORY not set. Run via: bazel run //<pkg>:db_migrate" >&2
  exit 2
fi

# Resolve DB env vars. Prefer PREFIX_DB_* if prefix is supplied, else fallback to DB_*
pick_var() {
  local name="$1"; local prefixed;
  if [[ -n "$ENV_PREFIX" ]]; then
    prefixed="${ENV_PREFIX}_${name}"
    if [[ -n "${!prefixed:-}" ]]; then
      printf '%s' "${!prefixed}"
      return 0
    fi
  fi
  if [[ -n "${!name:-}" ]]; then
    printf '%s' "${!name}"
    return 0
  fi
  return 1
}

# Load .env if present and vars not already set
if ! pick_var DB_URL >/dev/null || ! pick_var DB_USER >/dev/null || ! pick_var DB_PASSWORD >/dev/null; then
  if [[ -f "$WORKSPACE_DIR/.env" ]]; then
    set +u
    set -a
    # shellcheck disable=SC1090
    source <(tr -d '\r' < "$WORKSPACE_DIR/.env")
    set +a
    set -u
  fi
fi

DB_URL=$(pick_var DB_URL || true)
DB_USER=$(pick_var DB_USER || true)
DB_PASSWORD=$(pick_var DB_PASSWORD || true)

if [[ -z "$DB_URL" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "Database env vars missing. Expected ${ENV_PREFIX:+${ENV_PREFIX}_}DB_URL, ${ENV_PREFIX:+${ENV_PREFIX}_}DB_USER, ${ENV_PREFIX:+${ENV_PREFIX}_}DB_PASSWORD (or unprefixed)." >&2
  exit 2
fi

# If DB_URL points to localhost, rewrite for Docker network access
DB_URL=${DB_URL/localhost/host.docker.internal}
DB_URL=${DB_URL/127.0.0.1/host.docker.internal}

if [[ "$DB_URL" != jdbc:* ]]; then
  DB_URL="jdbc:$DB_URL"
fi

MOUNT_PATH="$WORKSPACE_DIR/$SQL_DIR"
if [[ ! -d "$MOUNT_PATH" ]]; then
  echo "SQL directory not found: $MOUNT_PATH" >&2
  exit 2
fi

exec docker run --rm \
  -v "$MOUNT_PATH":/flyway/sql \
  flyway/flyway \
  -url="$DB_URL" \
  -user="$DB_USER" \
  -password="$DB_PASSWORD" \
  migrate
