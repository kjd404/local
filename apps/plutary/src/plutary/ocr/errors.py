"""Domain-specific exceptions for OCR failures."""

from __future__ import annotations

from typing import Optional


class OcrProcessingError(RuntimeError):
    """Raised when OCR preprocessing or inference fails irrecoverably."""

    def __init__(self, message: str, *, cause: Optional[Exception] = None) -> None:
        super().__init__(message)
        self.__cause__ = cause
