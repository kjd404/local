"""Database configuration helpers for Plutary."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, MutableMapping


class DatabaseConfigError(RuntimeError):
    """Raised when database configuration cannot be resolved."""


@dataclass(frozen=True)
class DatabaseSettings:
    """Connection settings for PostgreSQL."""

    url: str
    user: str | None
    password: str | None

    @classmethod
    def from_sources(
        cls,
        *,
        explicit: Mapping[str, str | None] | None = None,
        environ: Mapping[str, str] | None = None,
        env_file: str | None = ".env",
    ) -> "DatabaseSettings":
        """Build settings from CLI args, environment, and optional .env file."""

        resolved: MutableMapping[str, str] = {}

        if env_file:
            file_path = Path(env_file)
            if file_path.exists():
                resolved.update(_parse_env_file(file_path))

        if environ is None:
            environ = os.environ
        resolved.update(
            {key: value for key, value in environ.items() if key.startswith("DB_")}
        )

        if explicit:
            for key, value in explicit.items():
                if value is not None:
                    resolved[key] = value

        url = resolved.get("DB_URL") or resolved.get("DATABASE_URL")
        if not url:
            raise DatabaseConfigError("DB_URL (or DATABASE_URL) must be provided")

        url = _normalize_url(url)

        user = resolved.get("DB_USER") or resolved.get("DATABASE_USER")
        password = resolved.get("DB_PASSWORD") or resolved.get("DATABASE_PASSWORD")
        return cls(url=url, user=user, password=password)


def _parse_env_file(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    for raw_line in path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        env[key] = value
    return env


def _normalize_url(url: str) -> str:
    cleaned = url.strip()
    if cleaned.startswith("jdbc:"):
        cleaned = cleaned[len("jdbc:") :]
    if cleaned.startswith("postgres://"):
        cleaned = "postgresql://" + cleaned[len("postgres://") :]
    return cleaned


__all__ = [
    "DatabaseConfigError",
    "DatabaseSettings",
]
