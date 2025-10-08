from __future__ import annotations

from pathlib import Path

import pytest

from plutary.persistence import DatabaseConfigError, DatabaseSettings


def test_settings_from_env_file(tmp_path: Path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text(
        "DB_URL=jdbc:postgresql://host/db\nDB_USER=test\nDB_PASSWORD=secret\n"
    )

    settings = DatabaseSettings.from_sources(env_file=str(env_file), environ={})

    assert settings.url == "postgresql://host/db"
    assert settings.user == "test"
    assert settings.password == "secret"


def test_settings_prefer_explicit_over_env(monkeypatch, tmp_path: Path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text("DB_URL=postgres://file/db\n")
    monkeypatch.setenv("DB_URL", "postgresql://env/db")

    settings = DatabaseSettings.from_sources(
        explicit={"DB_URL": "postgresql://cli/db"},
        env_file=str(env_file),
    )

    assert settings.url == "postgresql://cli/db"


def test_missing_url_raises_error(tmp_path: Path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text("DB_USER=test\n")

    with pytest.raises(DatabaseConfigError):
        DatabaseSettings.from_sources(env_file=str(env_file), environ={})
