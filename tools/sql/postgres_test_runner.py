"""Utility to run pytest with an ephemeral PostgreSQL database managed by Bazel."""

from __future__ import annotations

import argparse
import os
import socket
import subprocess
import sys
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator
import shutil
import stat

import psycopg
import pytest


class PostgresError(RuntimeError):
    """Raised when the PostgreSQL test harness fails."""


@contextmanager
def postgres_container(
    *,
    image: str,
    port: int,
    user: str,
    password: str,
    database: str,
) -> Iterator[tuple[str, dict[str, str]]]:
    """Start a disposable postgres container and yield connection settings."""

    container_name = f"bazel-testdb-{uuid.uuid4().hex[:12]}"
    env = {
        "POSTGRES_USER": user,
        "POSTGRES_PASSWORD": password,
        "POSTGRES_DB": database,
    }
    run_cmd = [
        "docker",
        "run",
        "-d",
        "--rm",
        "--name",
        container_name,
        "-p",
        f"{port}:5432",
    ]
    for key, value in env.items():
        run_cmd.extend(["-e", f"{key}={value}"])
    run_cmd.append(image)

    if shutil.which("docker") is None:
        raise PostgresError("Docker CLI not found in PATH.")

    try:
        subprocess.run(
            run_cmd,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        stdout = exc.stdout or ""
        stderr = exc.stderr or ""
        details = "\n".join(filter(None, [stdout.strip(), stderr.strip()]))
        raise PostgresError(
            f"Failed to start postgres container (exit {exc.returncode})."
            + (f"\n{details}" if details else "")
        ) from exc

    try:
        wait_for_ready(
            host="localhost", port=port, user=user, password=password, database=database
        )
        yield (
            container_name,
            {
                "host": "localhost",
                "port": str(port),
                "user": user,
                "password": password,
                "database": database,
            },
        )
    finally:
        subprocess.run(
            ["docker", "stop", container_name],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )


def wait_for_ready(
    *,
    host: str,
    port: int,
    user: str,
    password: str,
    database: str,
    timeout: float = 30.0,
) -> None:
    deadline = time.time() + timeout
    dsn = f"postgresql://{user}:{password}@{host}:{port}/{database}"
    while time.time() < deadline:
        try:
            with psycopg.connect(dsn, connect_timeout=3):
                return
        except psycopg.OperationalError:
            time.sleep(0.5)
    raise PostgresError("Timed out waiting for postgres to become ready.")


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("", 0))
        return sock.getsockname()[1]


def apply_migrations(
    *,
    sql_dir: str,
    env_prefix: str,
    history_table: str | None,
    db_settings: dict[str, str],
    workspace_dir: Path,
) -> None:
    script_path = locate_runfile(Path("tools/sql/flyway_migrate.sh"))
    if script_path is None:
        raise PostgresError("Unable to locate flyway_migrate.sh in runfiles.")

    if not os.access(script_path, os.X_OK):
        mode = script_path.stat().st_mode
        script_path.chmod(mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    env = os.environ.copy()
    db_url = f"postgresql://{db_settings['host']}:{db_settings['port']}/{db_settings['database']}"
    env_vars = {
        "DB_URL": db_url,
        "DB_USER": db_settings["user"],
        "DB_PASSWORD": db_settings["password"],
    }
    if env_prefix:
        env_vars.update(
            {
                f"{env_prefix}_DB_URL": db_url,
                f"{env_prefix}_DB_USER": db_settings["user"],
                f"{env_prefix}_DB_PASSWORD": db_settings["password"],
            }
        )
    env.update(env_vars)
    env["BUILD_WORKSPACE_DIRECTORY"] = str(workspace_dir)

    cmd = [str(script_path), f"--sql-dir={sql_dir}"]
    if env_prefix:
        cmd.append(f"--env-prefix={env_prefix}")
    if history_table:
        cmd.append(f"--history-table={history_table}")

    subprocess.run(cmd, check=True, env=env)


def resolve_workspace_dir() -> Path:
    runfiles_root = os.environ.get("TEST_SRCDIR")
    workspace_name = os.environ.get("TEST_WORKSPACE")
    if runfiles_root and workspace_name:
        candidate = Path(runfiles_root) / workspace_name
        if candidate.exists():
            return candidate
    # Fallback to BUILD_WORKSPACE_DIRECTORY if provided (e.g., bazel run).
    existing = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    if existing:
        return Path(existing)
    # As a last resort, assume current working directory is the workspace root.
    return Path.cwd()


def locate_runfile(relative: Path) -> Path | None:
    workspace = os.environ.get("TEST_WORKSPACE")
    runfiles_dir = os.environ.get("RUNFILES_DIR")
    if runfiles_dir and workspace:
        candidate = Path(runfiles_dir) / workspace / relative
        if candidate.exists():
            return candidate

    test_srcdir = os.environ.get("TEST_SRCDIR")
    if test_srcdir and workspace:
        candidate = Path(test_srcdir) / workspace / relative
        if candidate.exists():
            return candidate

    fallback = Path(__file__).resolve().parent / relative.name
    if fallback.exists():
        return fallback
    return None


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Run pytest with a temporary Postgres database."
    )
    parser.add_argument(
        "--sql-dir",
        required=True,
        help="Workspace-relative directory containing Flyway migrations.",
    )
    parser.add_argument(
        "--env-prefix",
        default="",
        help="Optional prefix for DB_* env vars (e.g. YONG).",
    )
    parser.add_argument(
        "--history-table", default=None, help="Optional Flyway history table name."
    )
    parser.add_argument(
        "--docker-image",
        default="postgres:15-alpine",
        help="Postgres image to use for tests.",
    )
    parser.add_argument("--db-user", default="tester")
    parser.add_argument("--db-password", default="tester")
    parser.add_argument("--db-name", default="testdb")
    known_args, pytest_args = parser.parse_known_args(argv)

    port = find_free_port()
    workspace_dir = resolve_workspace_dir()
    os.chdir(workspace_dir)

    with postgres_container(
        image=known_args.docker_image,
        port=port,
        user=known_args.db_user,
        password=known_args.db_password,
        database=known_args.db_name,
    ) as (_, settings):
        apply_migrations(
            sql_dir=known_args.sql_dir,
            env_prefix=known_args.env_prefix,
            history_table=known_args.history_table,
            db_settings=settings,
            workspace_dir=workspace_dir,
        )

        db_url_local = (
            f"postgresql://{settings['host']}:{settings['port']}/{settings['database']}"
        )
        os.environ.update(
            {
                "DB_URL": db_url_local,
                "DB_USER": settings["user"],
                "DB_PASSWORD": settings["password"],
            }
        )
        if known_args.env_prefix:
            os.environ.update(
                {
                    f"{known_args.env_prefix}_DB_URL": db_url_local,
                    f"{known_args.env_prefix}_DB_USER": settings["user"],
                    f"{known_args.env_prefix}_DB_PASSWORD": settings["password"],
                }
            )

        pytest_args = list(pytest_args)
        if not pytest_args:
            pytest_args = ["-q"]
        return pytest.main(pytest_args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
