#!/usr/bin/env bash
set -euo pipefail

# Run the ingest-service Docker image locally pointing at Postgres.
# Usage: bazel run //apps/ingest-service/docker:run_container -- [--image=tag] [--name=name]

WORKSPACE_DIR="${BUILD_WORKSPACE_DIRECTORY:-}"
if [[ -z "$WORKSPACE_DIR" ]]; then
  echo "BUILD_WORKSPACE_DIRECTORY not set. Run via: bazel run //apps/ingest-service/docker:run_container" >&2
  exit 2
fi

CONTAINER_NAME="${CONTAINER_NAME:-ingest-service}"
IMAGE_TAG="${IMAGE_TAG:-ingest-service:latest}"
DETACH="${DETACH:-true}"
RESTART="${RESTART:-false}"
# Host port to bind to container port 8080
HOST_PORT="${HOST_PORT:-8080}"
AUTO_PORT="${AUTO_PORT:-false}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name=*) CONTAINER_NAME="${1#--name=}"; shift;;
    --image=*) IMAGE_TAG="${1#--image=}"; shift;;
    --detach|--detached|-d) DETACH="true"; shift;;
    --attach|--foreground|-f) DETACH="false"; shift;;
    --restart) RESTART="true"; shift;;
    --no-restart) RESTART="false"; shift;;
    --host-port=*) HOST_PORT="${1#--host-port=}"; shift;;
    --port=*) HOST_PORT="${1#--port=}"; shift;;
    --auto-port) AUTO_PORT="true"; shift;;
    *) echo "Unknown option: $1" >&2; exit 2;;
  esac
done

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

# Inside Docker, 'localhost' refers to the container. If DB_URL points to localhost, rewrite to host.docker.internal
if [[ "$DB_URL" =~ ://(localhost|127\.0\.0\.1)(:|/) ]]; then
  DB_URL="${DB_URL/localhost/host.docker.internal}"
  DB_URL="${DB_URL/127.0.0.1/host.docker.internal}"
fi

# Handle existing container
RUNNING_ID=$(docker ps -q --filter "name=^/${CONTAINER_NAME}$" || true)
EXISTS_ID=$(docker ps -aq --filter "name=^/${CONTAINER_NAME}$" || true)

if [[ -n "$RUNNING_ID" ]]; then
  if [[ "$DETACH" == "false" ]]; then
    echo "Container ${CONTAINER_NAME} already running ($RUNNING_ID); attaching logs..." >&2
    exec docker logs -f "$CONTAINER_NAME"
  fi
  if [[ "$RESTART" != "true" ]]; then
    echo "Container ${CONTAINER_NAME} already running ($RUNNING_ID); use --restart to recreate or 'bazel run //apps/ingest-service/docker:stop_container' to stop." >&2
    exit 0
  fi
fi

# Remove stopped container with same name (or running one if --restart)
if [[ -n "$EXISTS_ID" ]]; then
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
fi

# Check for Docker port collision and optionally auto-pick a free port
if docker ps --filter "publish=${HOST_PORT}" --format '{{.Names}}' | grep -q .; then
  OCCUPANTS=$(docker ps --filter "publish=${HOST_PORT}" --format '{{.Names}}')
  if echo "$OCCUPANTS" | grep -qx "$CONTAINER_NAME"; then
    # Our name is already bound elsewhere somehow; fall through to recreate
    true
  else
    if [[ "$AUTO_PORT" == "true" ]]; then
      BASE_PORT="$HOST_PORT"
      for p in $(seq $((BASE_PORT+1)) $((BASE_PORT+50))); do
        if ! docker ps --filter "publish=${p}" --format '{{.Names}}' | grep -q .; then
          HOST_PORT="$p"
          echo "[run_container] Port ${BASE_PORT} busy (by: $OCCUPANTS). Using ${HOST_PORT}." >&2
          break
        fi
      done
    else
      echo "[run_container] Host port ${HOST_PORT} is already in use by: ${OCCUPANTS}." >&2
      echo "Specify a different port with --host-port=XXXX or enable auto selection with --auto-port." >&2
      exit 1
    fi
  fi
fi

RUN_FLAGS=(--rm --name "$CONTAINER_NAME" -p "${HOST_PORT}:8080")
if [[ "$DETACH" == "true" ]]; then
  RUN_FLAGS+=( -d )
fi

exec docker run "${RUN_FLAGS[@]}" \
  -e DB_URL="$DB_URL" -e DB_USER="$DB_USER" -e DB_PASSWORD="$DB_PASSWORD" \
  -v "$WORKSPACE_DIR/storage:/app/storage" \
  "$IMAGE_TAG"
