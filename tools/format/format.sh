#!/usr/bin/env bash
set -euo pipefail

# Aggregate formatter: BUILD/Starlark, SQL, and Java
# - Uses buildifier and pg_format from the host if available
# - Uses pre-commit's google-java-format mirror for Java

format_bazel_files() {
  if command -v buildifier >/dev/null 2>&1; then
    echo "[format] buildifier: formatting Bazel/Starlark files..."
    buildifier -mode=fix -r .
  else
    echo "[format] buildifier not found; skipping Bazel/Starlark formatting" >&2
  fi
}

format_sql_files() {
  # Find .sql files excluding common build output and VCS dirs
  mapfile -d '' sql_files < <(find . \
    -type d \( -name '.git' -o -name 'bazel-*' -o -name '.venv' -o -name 'target' -o -name 'build' \) -prune -false -o \
    -type f -name '*.sql' -print0)
  if (( ${#sql_files[@]} > 0 )); then
    if command -v pg_format >/dev/null 2>&1; then
      echo "[format] pg_format: formatting ${#sql_files[@]} SQL file(s)..."
      printf '%s\0' "${sql_files[@]}" | xargs -0 -n 100 pg_format -i -L
    else
      echo "[format] pg_format not found; skipping SQL formatting" >&2
    fi
  fi
}

format_java_files() {
  # Use pre-commit's google-java-format hook (local/system) to format all Java files.
  if command -v pre-commit >/dev/null 2>&1; then
    echo "[format] google-java-format (pre-commit mirror): formatting Java files..."
    # --all-files ensures full repo formatting when running the aggregate.
    pre-commit run google-java-format --all-files || true
  else
    echo "[format] pre-commit not found; skipping Java formatting" >&2
  fi
}

format_bazel_files
format_sql_files
format_java_files

echo "[format] Done."
