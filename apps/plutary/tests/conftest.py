from __future__ import annotations

from dataclasses import dataclass
import contextlib
import io

import psycopg
from psycopg import pq
import pytest

from plutary.cli import receipt_cli
from plutary.persistence import DatabaseSettings
from plutary.persistence.database import use_shared_connection

from .util import RecordFactory


@pytest.fixture(scope="session")
def db_settings() -> DatabaseSettings:
    return DatabaseSettings.from_sources(env_file=None)


@pytest.fixture(scope="session")
def shared_connection(db_settings: DatabaseSettings) -> psycopg.Connection:
    connection = psycopg.connect(
        db_settings.url,
        user=db_settings.user,
        password=db_settings.password,
    )
    try:
        yield connection
    finally:
        connection.close()


@pytest.fixture(autouse=True)
def transactional_db(shared_connection: psycopg.Connection) -> None:
    shared_connection.execute("BEGIN")
    with use_shared_connection(shared_connection):
        try:
            yield
        finally:
            if shared_connection.info.transaction_status != pq.TransactionStatus.IDLE:
                shared_connection.rollback()


@dataclass(frozen=True)
class CliResult:
    exit_code: int
    stdout: str
    stderr: str


@pytest.fixture
def record_factory(shared_connection: psycopg.Connection) -> RecordFactory:
    return RecordFactory(shared_connection)


@pytest.fixture
def cli_runner(db_settings: DatabaseSettings):
    def run_cli(argv: list[str]) -> CliResult:
        full_argv = list(argv)
        _ensure_cli_option(full_argv, "--db-url", db_settings.url)
        _ensure_cli_option(full_argv, "--db-user", db_settings.user)
        _ensure_cli_option(full_argv, "--db-password", db_settings.password)

        stdout = io.StringIO()
        stderr = io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            exit_code = receipt_cli.main(full_argv)
        return CliResult(
            exit_code=exit_code, stdout=stdout.getvalue(), stderr=stderr.getvalue()
        )

    return run_cli


def _ensure_cli_option(argv: list[str], name: str, value: str | None) -> None:
    if value is None:
        return
    if any(arg == name or arg.startswith(f"{name}=") for arg in argv):
        return
    argv.extend([name, value])
