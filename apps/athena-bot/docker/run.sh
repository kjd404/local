#!/usr/bin/env bash
set -euo pipefail

# Run the athena-bot Docker image locally, forwarding env vars from .env
# Usage: bazel run //apps/athena-bot/docker:run_container -- [--image=tag] [--name=name]

WORKSPACE_DIR="${BUILD_WORKSPACE_DIRECTORY:-}"
if [[ -z "$WORKSPACE_DIR" ]]; then
  echo "BUILD_WORKSPACE_DIRECTORY not set. Run via: bazel run //apps/athena-bot/docker:run_container" >&2
  exit 2
fi

CONTAINER_NAME="${CONTAINER_NAME:-athena-bot}"
IMAGE_TAG="${IMAGE_TAG:-athena-bot:latest}"
DETACH="${DETACH:-true}"
RESTART="${RESTART:-false}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name=*) CONTAINER_NAME="${1#--name=}"; shift;;
    --image=*) IMAGE_TAG="${1#--image=}"; shift;;
    --detach|--detached|-d) DETACH="true"; shift;;
    --attach|--foreground|-f) DETACH="false"; shift;;
    --restart) RESTART="true"; shift;;
    --no-restart) RESTART="false"; shift;;
    *) echo "Unknown option: $1" >&2; exit 2;;
  esac
done

# Load .env if present and token not already set
if [[ -z "${DISCORD_BOT_TOKEN:-}" ]]; then
  if [[ -f "$WORKSPACE_DIR/.env" ]]; then
    set +u
    set -a
    # shellcheck disable=SC1090
    source <(tr -d '\r' < "$WORKSPACE_DIR/.env")
    set +a
    set -u
  fi
fi

: "${DISCORD_BOT_TOKEN:?DISCORD_BOT_TOKEN is required}"

# Handle existing container
RUNNING_ID=$(docker ps -q --filter "name=^/${CONTAINER_NAME}$" || true)
EXISTS_ID=$(docker ps -aq --filter "name=^/${CONTAINER_NAME}$" || true)

if [[ -n "$RUNNING_ID" ]]; then
  if [[ "$DETACH" == "false" ]]; then
    echo "Container ${CONTAINER_NAME} already running ($RUNNING_ID); attaching logs..." >&2
    exec docker logs -f "$CONTAINER_NAME"
  fi
  if [[ "$RESTART" != "true" ]]; then
    echo "Container ${CONTAINER_NAME} already running ($RUNNING_ID); use --restart to recreate or 'bazel run //apps/athena-bot/docker:stop_container' to stop." >&2
    exit 0
  fi
fi

if [[ -n "$EXISTS_ID" ]]; then
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
fi

RUN_FLAGS=(--rm --name "$CONTAINER_NAME")
if [[ "$DETACH" == "true" ]]; then
  RUN_FLAGS+=( -d )
fi

exec docker run "${RUN_FLAGS[@]}" \
  -e DISCORD_BOT_TOKEN="$DISCORD_BOT_TOKEN" \
  "$IMAGE_TAG"
