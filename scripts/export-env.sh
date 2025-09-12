#!/usr/bin/env bash
# Usage: source scripts/export-env.sh
# Loads environment variables from .env into the current shell, if present.

ENV_FILE=".env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env missing" >&2
  # If sourced, return; if executed, exit (avoid SC2317)
  if [[ "${BASH_SOURCE[0]-$0}" != "$0" ]]; then
    return 1
  else
    exit 1
  fi
fi

# Export variables defined in .env, trimming CR for cross-platform compat
set -a
# shellcheck disable=SC1090
source <(tr -d '\r' < "$ENV_FILE")
set +a
