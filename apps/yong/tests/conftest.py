from __future__ import annotations

import psycopg
from psycopg import pq
import pytest

from yong.persistence import DatabaseSettings
from yong.persistence.database import use_shared_connection


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
