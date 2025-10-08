"""Helpers for emitting human-readable step traces during OCR processing."""

from __future__ import annotations

from typing import Callable

StepReporter = Callable[[str, str], None]


def noop_reporter(
    stage: str, message: str
) -> None:  # pragma: no cover - trivial helper
    del stage, message
