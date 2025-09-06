#!/usr/bin/env bash
# Usage: source scripts/export-env.sh
# Loads environment variables from .env into the current shell, if present.

ENV_FILE=".env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env missing" >&2
  # If sourced, return; if executed, exit
  return 1 2>/dev/null || exit 1
fi

# Export variables defined in .env, trimming CR for cross-platform compat
set -a
# shellcheck disable=SC1090
source <(tr -d '\r' < "$ENV_FILE")
set +a
