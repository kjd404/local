from __future__ import annotations

import io

from PIL import Image
import pytest

from plutary.ocr import DefaultReceiptImagePreprocessor, ReceiptImage
from plutary.ocr.errors import OcrProcessingError


def _landscape_png() -> bytes:
    with Image.new("RGB", (40, 20), color="white") as image:
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        return buffer.getvalue()


def test_default_preprocessor_rotates_landscape_images() -> None:
    preprocessor = DefaultReceiptImagePreprocessor()

    processed = preprocessor.preprocess(ReceiptImage(_landscape_png()))

    with Image.open(io.BytesIO(processed)) as transformed:
        assert transformed.height >= transformed.width


def test_default_preprocessor_rejects_empty_images() -> None:
    preprocessor = DefaultReceiptImagePreprocessor()

    with pytest.raises(OcrProcessingError):
        preprocessor.preprocess(ReceiptImage(b""))
