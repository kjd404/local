"""OCR components for Yong's Python implementation."""

from .errors import OcrProcessingError
from .interfaces import ReceiptOcrService
from .paddle_service import PaddleReceiptOcrService
from .preprocessing import (
    DefaultReceiptImagePreprocessor,
    HybridReceiptImagePreprocessor,
    NoOpReceiptImagePreprocessor,
    OpenCvReceiptImageProcessor,
)
from .receipt_image import ReceiptImage
from .result import OcrResult

__all__ = [
    "OcrProcessingError",
    "ReceiptOcrService",
    "PaddleReceiptOcrService",
    "ReceiptImage",
    "OcrResult",
    "DefaultReceiptImagePreprocessor",
    "HybridReceiptImagePreprocessor",
    "NoOpReceiptImagePreprocessor",
    "OpenCvReceiptImageProcessor",
]
