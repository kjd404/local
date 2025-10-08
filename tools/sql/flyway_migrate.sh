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
HISTORY_TABLE=""
SHOW_HELP=0
for arg in "$@"; do
  case "$arg" in
    --sql-dir=*) SQL_DIR="${arg#*=}" ;;
    --env-prefix=*) ENV_PREFIX="${arg#*=}" ;;
    --history-table=*) HISTORY_TABLE="${arg#*=}" ;;
    --help|-h) SHOW_HELP=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

if [[ $SHOW_HELP -eq 1 ]]; then
  cat <<'USAGE'
Usage: flyway_migrate.sh --sql-dir=<relative-path> [--env-prefix=PREFIX] [--history-table=TABLE]

Executes Flyway migrations located under the workspace-relative SQL directory. The script reads
database connection details from either PREFIX_DB_* variables (when --env-prefix is supplied) or
fallback DB_* variables/.env, then runs Flyway inside a disposable Docker container.

Environment:
  BUILD_WORKSPACE_DIRECTORY  Required; set automatically when invoking via `bazel run`.
  [PREFIX_]DB_URL            PostgreSQL JDBC/URL (jdbc: prefix optional).
  [PREFIX_]DB_USER           Database user.
  [PREFIX_]DB_PASSWORD       Database password.

Examples:
  bazel run //ops/sql/plutary:db_migrate
  bazel run //ops/sql/plutary:db_migrate -- --env-prefix=PLUTARY
  bazel run //ops/sql/plutary:db_migrate -- --help
USAGE
  exit 0
fi

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

if [[ -n "${FLYWAY_SQL_ABS_DIR:-}" ]]; then
  MOUNT_PATH="$FLYWAY_SQL_ABS_DIR"
else
  MOUNT_PATH="$WORKSPACE_DIR/$SQL_DIR"
fi
if [[ -n "$HISTORY_TABLE" ]]; then
  HISTORY_ARGS=(-table="$HISTORY_TABLE")
else
  HISTORY_ARGS=()
fi

if [[ ! -d "$MOUNT_PATH" ]]; then
  echo "SQL directory not found: $MOUNT_PATH" >&2
  exit 2
fi

run_flyway() {
  docker run --rm \
    -v "$MOUNT_PATH":/flyway/sql \
    flyway/flyway \
    -url="$DB_URL" \
    -user="$DB_USER" \
    -password="$DB_PASSWORD" \
    -locations=filesystem:/flyway/sql \
    "${HISTORY_ARGS[@]}" \
    "$@"
}

if [[ -n "$HISTORY_TABLE" ]]; then
  set +e
  run_flyway -baselineVersion=0 baseline
  status=$?
  set -e
  if [[ $status -ne 0 && $status -ne 1 ]]; then
    echo "Flyway baseline failed (exit $status)" >&2
    exit $status
  fi
fi

run_flyway migrate
