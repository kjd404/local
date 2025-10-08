"""OCR adapter backed by PaddleOCR."""

from __future__ import annotations

import io
from typing import Iterable, Protocol
from collections.abc import Sequence

import numpy as np
from PIL import Image

from .errors import OcrProcessingError
from .interfaces import ReceiptImagePreprocessor, ReceiptOcrService
from .receipt_image import ReceiptImage
from .reporting import StepReporter, noop_reporter
from .result import OcrResult

try:  # Rely on OpenCV when present for fast decoding.
    import cv2  # type: ignore[import]
except Exception:  # pragma: no cover - OpenCV may be missing in constrained envs.
    cv2 = None  # type: ignore[assignment]


class PaddleOcrEngine(Protocol):
    """Subset of PaddleOCR API used by Plutary."""

    def ocr(
        self, img: np.ndarray, cls: bool = True
    ) -> list:  # pragma: no cover - protocol stub
        ...


class PaddleReceiptOcrService(ReceiptOcrService):
    """Runs OCR using PaddleOCR with preprocessing and rotation heuristics."""

    _RECEIPT_KEYWORDS = (
        "TOTAL",
        "SUBTOTAL",
        "SUMMARY",
        "TAX",
        "AMOUNT",
        "BALANCE",
        "MERCHANT",
        "RECEIPT",
    )
    _HIGH_CONFIDENCE_SCORE = 120
    _ROTATION_THRESHOLD_SCORE = 60

    def __init__(
        self,
        engine: PaddleOcrEngine,
        preprocessor: ReceiptImagePreprocessor,
        *,
        rotation_angles: Iterable[int] = (0, 90, 180, 270),
        step_reporter: StepReporter | None = None,
    ) -> None:
        self._engine = engine
        self._preprocessor = preprocessor
        self._rotation_angles = list(rotation_angles)
        if 0 not in self._rotation_angles:
            self._rotation_angles.insert(0, 0)
        self._report = step_reporter or noop_reporter

    def extract_text(self, image: ReceiptImage) -> OcrResult:
        if image is None:
            raise ValueError("image must not be None")
        if image.is_empty():
            raise OcrProcessingError("Receipt image bytes are empty.")

        self._report("preprocess", "Starting receipt preprocessing pipeline")
        processed = self._preprocessor.preprocess(image)
        best_text = ""
        best_score = -1
        initial_text = ""

        for angle in self._rotation_angles:
            self._report("ocr", f"Running PaddleOCR at rotation {angle}°")
            try:
                oriented_bytes = (
                    processed
                    if angle % 360 == 0
                    else self._rotate_png(processed, angle)
                )
            except Exception as exc:
                raise OcrProcessingError("Failed to rotate receipt image") from exc

            text = self._run_ocr(oriented_bytes)
            if not initial_text:
                initial_text = text
            score = self._receipt_score(text)
            if score > best_score:
                self._report(
                    "ocr",
                    f"Rotation {angle}° improved score to {score}",
                )
                best_score = score
                best_text = text
            if best_score >= self._HIGH_CONFIDENCE_SCORE:
                self._report(
                    "ocr", "High-confidence result achieved; stopping rotation search"
                )
                break
            if angle == 0 and best_score >= self._ROTATION_THRESHOLD_SCORE:
                self._report(
                    "ocr",
                    "Base orientation met rotation threshold; skipping additional angles",
                )
                break

        final_text = best_text.strip()
        if self._looks_unintelligible(final_text) and initial_text:
            self._report(
                "ocr",
                "Best rotation looked unintelligible; falling back to initial orientation text",
            )
            return OcrResult(initial_text.strip())
        self._report("ocr", "OCR completed successfully")
        return OcrResult(final_text)

    def _run_ocr(self, image_bytes: bytes) -> str:
        try:
            matrix = self._decode_to_matrix(image_bytes)
            if matrix is None:
                raise OcrProcessingError("Unable to decode processed receipt image.")
            result = self._engine.ocr(matrix)
        except OcrProcessingError:
            raise
        except Exception as exc:  # pragma: no cover - Paddle internal failures.
            raise OcrProcessingError(f"Paddle OCR execution failed: {exc}") from exc
        return self._concatenate_text(result)

    def _decode_to_matrix(self, payload: bytes) -> np.ndarray | None:
        if cv2 is not None:
            array = np.frombuffer(payload, dtype=np.uint8)
            if array.size == 0:
                return None
            decoded = cv2.imdecode(array, cv2.IMREAD_COLOR)
            if decoded is None or decoded.size == 0:
                return None
            return decoded
        with Image.open(io.BytesIO(payload)) as image:
            return np.array(image.convert("RGB"))

    def _rotate_png(self, payload: bytes, degrees: int) -> bytes:
        if degrees % 360 == 0:
            return payload
        with Image.open(io.BytesIO(payload)) as image:
            rotated = image.rotate(-degrees, expand=True, fillcolor="white")
            buffer = io.BytesIO()
            rotated.save(buffer, format="PNG")
            return buffer.getvalue()

    def _concatenate_text(self, raw_result: list) -> str:
        lines: list[str] = []
        for item in raw_result or []:
            text = None
            if isinstance(item, dict):
                texts = item.get("rec_texts")
                if isinstance(texts, Sequence) and texts:
                    lines.extend(str(t).strip() for t in texts if str(t).strip())
                    continue
                text = item.get("text") or item.get("label")
            elif isinstance(item, Sequence) and len(item) >= 2:
                candidate = item[1]
                if isinstance(candidate, dict):
                    texts = candidate.get("rec_texts")
                    if isinstance(texts, Sequence) and texts:
                        lines.extend(str(t).strip() for t in texts if str(t).strip())
                        continue
                    text = candidate.get("text") or candidate.get("label")
                elif isinstance(candidate, Sequence) and candidate:
                    text = candidate[0]
                else:
                    text = candidate
            else:
                continue
            if text:
                stripped = str(text).strip()
                if stripped:
                    lines.append(stripped)
        return "\n".join(line for line in lines if line).strip()

    def _looks_unintelligible(self, text: str) -> bool:
        return self._alpha_numeric_score(text) < 12

    def _receipt_score(self, text: str) -> int:
        if not text:
            return 0
        score = self._alpha_numeric_score(text)
        upper = text.upper()
        for keyword in self._RECEIPT_KEYWORDS:
            if keyword in upper:
                score += 50
        return score

    def _alpha_numeric_score(self, text: str) -> int:
        return sum(1 for char in text if char.isalnum())
