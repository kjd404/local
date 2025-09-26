"""Database access utilities for Yong."""

from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from typing import Iterator

import uuid

import psycopg

from .config import DatabaseSettings


_TEST_CONNECTION: ContextVar[psycopg.Connection | None] = ContextVar(
    "_yong_test_connection", default=None
)


class Database:
    """Thin wrapper around psycopg connections."""

    def __init__(self, settings: DatabaseSettings) -> None:
        self._settings = settings

    @contextmanager
    def connect(self) -> Iterator[psycopg.Connection]:
        override = _TEST_CONNECTION.get()
        savepoint = None
        if override is not None:
            connection = override
            savepoint = f"yong_test_{uuid.uuid4().hex}"
            connection.execute(f"SAVEPOINT {savepoint}")
        else:
            connection = psycopg.connect(
                self._settings.url,
                user=self._settings.user,
                password=self._settings.password,
            )
        try:
            yield connection
            if savepoint is not None:
                connection.execute(f"RELEASE SAVEPOINT {savepoint}")
            else:
                connection.commit()
        except Exception:
            if savepoint is not None:
                connection.execute(f"ROLLBACK TO SAVEPOINT {savepoint}")
            else:
                connection.rollback()
            raise
        finally:
            if savepoint is None:
                connection.close()


@contextmanager
def use_shared_connection(connection: psycopg.Connection) -> Iterator[None]:
    """Bind a shared connection for the duration of the context (testing helper)."""

    token = _TEST_CONNECTION.set(connection)
    try:
        yield
    finally:
        _TEST_CONNECTION.reset(token)


__all__ = ["Database", "use_shared_connection"]
