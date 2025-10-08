"""OCR result value object."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class OcrResult:
    """Holds the extracted text for a receipt image."""

    text: str

    def __post_init__(self) -> None:
        if self.text is None:
            raise ValueError("text must not be None")

    def is_empty(self) -> bool:
        return not self.text.strip()
