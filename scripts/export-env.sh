#!/usr/bin/env bash

ENV_FILE=".env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env missing"
  return 1 2>/dev/null || exit 1
fi

set -a
# shellcheck disable=SC1090
source <(tr -d '\r' < "$ENV_FILE")
set +a
