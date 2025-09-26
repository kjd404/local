from __future__ import annotations

import io
from dataclasses import dataclass

from PIL import Image
import numpy as np
import pytest

from yong.ocr import (
    NoOpReceiptImagePreprocessor,
    PaddleReceiptOcrService,
    ReceiptImage,
)
from yong.ocr.errors import OcrProcessingError


@dataclass
class ScriptedResponse:
    text: str


class FakeOcrEngine:
    def __init__(self, responses: list[ScriptedResponse] | Exception) -> None:
        self._responses = responses
        self.calls: list[np.ndarray] = []
        self._raise = None
        if isinstance(responses, Exception):
            self._raise = responses
            self._responses = []  # type: ignore[assignment]

    def ocr(self, img: np.ndarray, cls: bool = True) -> list:
        self.calls.append(img)
        if self._raise is not None:
            raise self._raise
        if not self._responses:
            return []
        scripted = self._responses.pop(0)
        return [(((0, 0), (1, 0), (1, 1), (0, 1)), (scripted.text, 0.9))]


def _png_bytes(width: int = 10, height: int = 20) -> bytes:
    with Image.new("RGB", (width, height), color="white") as image:
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        return buffer.getvalue()


def test_extracts_trimmed_text_from_successful_run() -> None:
    engine = FakeOcrEngine([ScriptedResponse(" SUMMARY HELLO WORLD 123 ")])
    service = PaddleReceiptOcrService(engine, NoOpReceiptImagePreprocessor())

    result = service.extract_text(ReceiptImage(_png_bytes()))

    assert result.text == "SUMMARY HELLO WORLD 123"
    assert len(engine.calls) == 1


def test_surfaces_process_failures() -> None:
    engine = FakeOcrEngine(OcrProcessingError("boom"))
    service = PaddleReceiptOcrService(engine, NoOpReceiptImagePreprocessor())

    with pytest.raises(OcrProcessingError) as exc:
        service.extract_text(ReceiptImage(_png_bytes()))

    assert "boom" in str(exc.value)


def test_attempts_rotations_when_low_score() -> None:
    engine = FakeOcrEngine(
        [
            ScriptedResponse("???"),
            ScriptedResponse("TOTAL AMOUNT 12345"),
        ]
    )
    service = PaddleReceiptOcrService(
        engine,
        NoOpReceiptImagePreprocessor(),
        rotation_angles=(0, 90),
    )

    result = service.extract_text(ReceiptImage(_png_bytes(width=20, height=10)))

    assert result.text == "TOTAL AMOUNT 12345"
    assert len(engine.calls) == 2


def test_returns_initial_text_when_best_unintelligible() -> None:
    engine = FakeOcrEngine([ScriptedResponse("@@@@@@")])
    service = PaddleReceiptOcrService(engine, NoOpReceiptImagePreprocessor())

    result = service.extract_text(ReceiptImage(_png_bytes()))

    assert result.text == "@@@@@@"
