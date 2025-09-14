#!/usr/bin/env bash
set -euo pipefail

# Build Docker image for athena-bot using repo's pinned requirements.lock
# Usage: bazel run //apps/athena-bot/docker:build_image -- [--tag=name]

TAG="${TAG:-athena-bot:latest}"
if [[ $# -ge 1 ]]; then
  case "$1" in
    --tag=*) TAG="${1#--tag=}"; shift;;
    *) TAG="$1"; shift;;
  esac
fi

WORKSPACE_DIR="${BUILD_WORKSPACE_DIRECTORY:-}"
if [[ -z "$WORKSPACE_DIR" ]]; then
  echo "BUILD_WORKSPACE_DIRECTORY not set. Run via: bazel run //apps/athena-bot/docker:build_image" >&2
  exit 2
fi

DOCKER_DIR="$WORKSPACE_DIR/apps/athena-bot/docker"

echo "[build_image] Staging requirements.lock to docker context" >&2
cp -f "$WORKSPACE_DIR/requirements.lock" "$DOCKER_DIR/requirements.lock"

echo "[build_image] docker build -t $TAG apps/athena-bot/docker" >&2
exec docker build -t "$TAG" "$DOCKER_DIR"
