#!/usr/bin/env bash
set -euo pipefail

# Build Docker image using Bazel-built deploy jar.
# Usage: bazel run //apps/ingest-service/docker:build_image -- [--tag=name]

TAG="${TAG:-ingest-service:latest}"
if [[ $# -ge 1 ]]; then
  case "$1" in
    --tag=*) TAG="${1#--tag=}"; shift;;
    *) TAG="$1"; shift;;
  esac
fi

WORKSPACE_DIR="${BUILD_WORKSPACE_DIRECTORY:-}"
if [[ -z "$WORKSPACE_DIR" ]]; then
  echo "BUILD_WORKSPACE_DIRECTORY not set. Run via: bazel run //apps/ingest-service/docker:build_image" >&2
  exit 2
fi

DOCKER_DIR="$WORKSPACE_DIR/apps/ingest-service/docker"

echo "[build_image] Building deploy jar via Bazel..." >&2
cd "$WORKSPACE_DIR"
bazel build //apps/ingest-service:ingest_app_deploy.jar >/dev/null

BIN_DIR=$(bazel info bazel-bin)
DEPLOY_JAR="$BIN_DIR/apps/ingest-service/ingest_app_deploy.jar"

echo "[build_image] Staging jar to docker context" >&2
cp -f "$DEPLOY_JAR" "$DOCKER_DIR/app.jar"

echo "[build_image] docker build -t $TAG apps/ingest-service/docker" >&2
exec docker build -t "$TAG" "$DOCKER_DIR"

