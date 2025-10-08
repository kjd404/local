"""In-memory representation of an uploaded receipt image."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class ReceiptImage:
    """Wraps raw bytes and content type for a receipt image."""

    bytes: bytes
    content_type: Optional[str] = None

    def __post_init__(self) -> None:
        if self.bytes is None:
            raise ValueError("bytes must not be None")
        if not isinstance(self.bytes, (bytes, bytearray)):
            raise TypeError("bytes must be bytes-like")

    def is_empty(self) -> bool:
        return len(self.bytes) == 0
