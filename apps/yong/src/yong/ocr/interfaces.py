"""Interfaces for OCR components."""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import NamedTuple

from .receipt_image import ReceiptImage
from .result import OcrResult


class ReceiptOcrService(ABC):
    """Contract for OCR services that can extract text from receipt images."""

    @abstractmethod
    def extract_text(self, image: ReceiptImage) -> OcrResult:
        """Runs OCR and returns structured text for the provided image."""


class ReceiptImagePreprocessor(ABC):
    """Transforms incoming receipt images into OCR-friendly representations."""

    @abstractmethod
    def preprocess(self, image: ReceiptImage) -> bytes:
        """Returns processed image bytes suitable for OCR engines."""


class AdvancedReceiptImageProcessor(ABC):
    """Optional OpenCV-backed preprocessor that can provide richer metrics."""

    @abstractmethod
    def preprocess(self, image: ReceiptImage) -> "ProcessedImage | None":
        """Attempts to improve the image; returns None when no improvement exists."""


class ProcessedImage(NamedTuple):
    """Describes the outcome of a successful advanced preprocessing pass."""

    bytes: bytes
    width: int
    height: int
    dynamic_range: float
    texture_ratio: float
