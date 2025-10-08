"""Preprocessing pipeline for receipt images prior to OCR."""

from __future__ import annotations

import io
from dataclasses import dataclass
from typing import Optional

import numpy as np
from PIL import Image, ImageEnhance, ImageOps

from .errors import OcrProcessingError
from .interfaces import (
    AdvancedReceiptImageProcessor,
    ProcessedImage,
    ReceiptImagePreprocessor,
)
from .receipt_image import ReceiptImage
from .reporting import StepReporter, noop_reporter

try:  # OpenCV is optional at runtime but improves quality when present.
    import cv2  # type: ignore[import]
except Exception:  # pragma: no cover - fallback when OpenCV is unavailable.
    cv2 = None  # type: ignore[assignment]


class NoOpReceiptImagePreprocessor(ReceiptImagePreprocessor):
    """Returns the original bytes unchanged."""

    def __init__(self, reporter: StepReporter | None = None) -> None:
        self._report = reporter or noop_reporter

    def preprocess(self, image: ReceiptImage) -> bytes:
        if image is None:
            raise ValueError("image must not be None")
        self._report(
            "preprocess", "No-op preprocessor applied (bytes returned unchanged)"
        )
        return bytes(image.bytes)


class DefaultReceiptImagePreprocessor(ReceiptImagePreprocessor):
    """Performs EXIF orientation correction, grayscale conversion, and contrast tweaks."""

    _CONTRAST_SCALE = 1.6
    _CONTRAST_OFFSET = -30.0

    def __init__(self, reporter: StepReporter | None = None) -> None:
        self._report = reporter or noop_reporter

    def preprocess(self, image: ReceiptImage) -> bytes:
        if image is None:
            raise ValueError("image must not be None")
        if image.is_empty():
            raise OcrProcessingError("Cannot preprocess empty receipt image.")

        try:
            with Image.open(io.BytesIO(image.bytes)) as source:
                self._report(
                    "preprocess",
                    f"Loaded image {source.width}x{source.height} (mode={source.mode}); applying EXIF transpose",
                )
                rgb = source.convert("RGB")
                transposed = ImageOps.exif_transpose(rgb)
                portrait = self._ensure_portrait(transposed)
                if portrait.height < portrait.width:
                    self._report(
                        "preprocess", "Image remains landscape after EXIF transpose"
                    )
                grayscale = ImageOps.grayscale(portrait)
                self._report("preprocess", "Converted to grayscale; boosting contrast")
                enhanced = self._enhance_contrast(grayscale)
                buffer = io.BytesIO()
                enhanced.save(buffer, format="PNG")
                self._report("preprocess", "Default preprocessing complete")
                return buffer.getvalue()
        except OcrProcessingError:
            raise
        except Exception as exc:  # pragma: no cover - pillow may raise various errors.
            raise OcrProcessingError("Failed to preprocess receipt image") from exc

    def _ensure_portrait(self, image: Image.Image) -> Image.Image:
        if image.height >= image.width:
            return image
        return image.rotate(90, expand=True, fillcolor="white")

    def _enhance_contrast(self, grayscale: Image.Image) -> Image.Image:
        # Apply linear contrast scaling similar to the Java RescaleOp pipeline.
        arr = np.asarray(grayscale, dtype=np.float32)
        arr = arr * self._CONTRAST_SCALE + self._CONTRAST_OFFSET
        arr = np.clip(arr, 0, 255).astype(np.uint8)
        boosted = Image.fromarray(arr)
        if boosted.mode != "L":
            boosted = boosted.convert("L")
        # A light local contrast enhancement helps with faint text.
        return ImageEnhance.Contrast(boosted).enhance(1.1)


@dataclass(frozen=True)
class _AdvancedMetrics:
    width: int
    height: int
    dynamic_range: float
    texture_ratio: float


class OpenCvReceiptImageProcessor(AdvancedReceiptImageProcessor):
    """Advanced preprocessing using OpenCV heuristics. Falls back silently on failure."""

    _BACKGROUND_KERNEL = (45, 45)
    _EDGE_KERNEL = (5, 5)
    _MORPH_KERNEL = (3, 3)
    _CLAHE_TILE_GRID = (8, 8)
    _RECEIPT_MIN_AREA_RATIO = 0.18
    _NON_ZERO_THRESHOLD = 0.018
    _TARGET_MIN_WIDTH = 1400

    def __init__(self, reporter: StepReporter | None = None) -> None:
        self._report = reporter or noop_reporter

    def preprocess(self, image: ReceiptImage) -> Optional[ProcessedImage]:
        if image is None or image.is_empty():
            return None
        if cv2 is None:
            self._report(
                "preprocess", "OpenCV not available; skipping advanced preprocessing"
            )
            return None

        decoded = self._decode(image.bytes)
        if decoded is None:
            self._report("preprocess", "OpenCV failed to decode image; falling back")
            return None

        try:
            self._report(
                "preprocess",
                f"Running OpenCV pipeline on {decoded.shape[1]}x{decoded.shape[0]} frame",
            )
            illumination = self._normalize_illumination(decoded)
            isolated = self._isolate_receipt(decoded, illumination)
            base = isolated if isolated is not None else decoded
            grayscale = cv2.cvtColor(base, cv2.COLOR_BGR2GRAY)
            deskewed = self._deskew(grayscale)
            enhanced = self._enhance_for_ocr(deskewed)
            metrics = self._measure(enhanced)
            if not self._looks_usable(metrics):
                self._report(
                    "preprocess",
                    "OpenCV metrics below threshold; falling back to default preprocessor",
                )
                return None
            success, encoded = cv2.imencode(".png", enhanced)
            if not success:
                self._report(
                    "preprocess", "OpenCV failed to encode enhanced image; falling back"
                )
                return None
            self._report(
                "preprocess",
                "OpenCV preprocessing succeeded (dynamic range %.2f, texture %.3f)"
                % (metrics.dynamic_range, metrics.texture_ratio),
            )
            return ProcessedImage(
                bytes(encoded.tobytes()),
                metrics.width,
                metrics.height,
                metrics.dynamic_range,
                metrics.texture_ratio,
            )
        except Exception:  # pragma: no cover - OpenCV failures fall back to defaults.
            self._report(
                "preprocess",
                "OpenCV pipeline raised; falling back to default preprocessor",
            )
            return None

    def _decode(self, payload: bytes) -> np.ndarray | None:
        array = np.frombuffer(payload, dtype=np.uint8)
        if array.size == 0:
            return None
        decoded = cv2.imdecode(array, cv2.IMREAD_COLOR)
        if decoded is None or decoded.size == 0:
            return None
        return decoded

    def _normalize_illumination(self, color: np.ndarray) -> np.ndarray:
        grayscale = cv2.cvtColor(color, cv2.COLOR_BGR2GRAY)
        blurred = cv2.GaussianBlur(grayscale, (3, 3), 0)
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, self._BACKGROUND_KERNEL)
        background = cv2.morphologyEx(blurred, cv2.MORPH_CLOSE, kernel)
        normalized = cv2.absdiff(background, blurred)
        normalized = cv2.normalize(normalized, None, 0, 255, cv2.NORM_MINMAX)
        return normalized.astype(np.uint8)

    def _isolate_receipt(
        self, original: np.ndarray, illumination: np.ndarray
    ) -> np.ndarray | None:
        edges = cv2.Canny(illumination, 70, 200)
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, self._EDGE_KERNEL)
        dilated = cv2.dilate(edges, kernel)
        contours, _ = cv2.findContours(
            dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
        )
        image_area = float(original.shape[0] * original.shape[1])
        best_rect = None
        best_area = 0.0

        for contour in contours:
            if contour.size == 0:
                continue
            area = abs(cv2.contourArea(contour))
            if area < image_area * self._RECEIPT_MIN_AREA_RATIO:
                continue
            rect = cv2.minAreaRect(contour.astype(np.float32))
            width, height = rect[1]
            rect_area = width * height
            if rect_area > best_area:
                best_area = rect_area
                best_rect = rect

        if best_rect is None:
            return None
        return self._crop(original, best_rect)

    def _crop(self, color: np.ndarray, rect: tuple) -> np.ndarray | None:
        (center_x, center_y), (width, height), angle = rect
        if width <= 0 or height <= 0:
            return None
        if angle < -45:
            angle += 90
            width, height = height, width

        matrix = cv2.getRotationMatrix2D((center_x, center_y), angle, 1.0)
        rotated = cv2.warpAffine(
            color,
            matrix,
            (color.shape[1], color.shape[0]),
            flags=cv2.INTER_CUBIC,
            borderMode=cv2.BORDER_REPLICATE,
            borderValue=(255, 255, 255),
        )
        x = max(int(center_x - width / 2), 0)
        y = max(int(center_y - height / 2), 0)
        w = min(int(width), rotated.shape[1] - x)
        h = min(int(height), rotated.shape[0] - y)
        if w <= 0 or h <= 0:
            return None
        cropped = rotated[y : y + h, x : x + w]
        if cropped.shape[0] < cropped.shape[1]:
            cropped = cv2.rotate(cropped, cv2.ROTATE_90_CLOCKWISE)
        return cropped

    def _deskew(self, grayscale: np.ndarray) -> np.ndarray:
        _, binary = cv2.threshold(
            grayscale, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
        )
        binary = cv2.bitwise_not(binary)
        coords = np.column_stack(np.where(binary > 0))
        if coords.size == 0:
            return grayscale
        rect = cv2.minAreaRect(coords.astype(np.float32))
        angle = rect[2]
        if angle < -45:
            angle = -(90 + angle)
        else:
            angle = -angle
        if abs(angle) < 0.2 or abs(angle) > 45:
            return grayscale
        height, width = grayscale.shape
        matrix = cv2.getRotationMatrix2D((width / 2, height / 2), angle, 1.0)
        rotated = cv2.warpAffine(
            grayscale,
            matrix,
            (width, height),
            flags=cv2.INTER_CUBIC,
            borderMode=cv2.BORDER_REPLICATE,
        )
        return rotated

    def _enhance_for_ocr(self, grayscale: np.ndarray) -> np.ndarray:
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=self._CLAHE_TILE_GRID)
        clahe_result = clahe.apply(grayscale)
        denoised = cv2.bilateralFilter(clahe_result, 5, 75, 75)
        leveled = cv2.normalize(denoised, None, 0, 255, cv2.NORM_MINMAX)
        blurred = cv2.medianBlur(leveled, 3)
        resized = self._maybe_upscale(blurred)
        trimmed = self._trim_borders(resized)
        return trimmed

    def _maybe_upscale(self, image: np.ndarray) -> np.ndarray:
        height, width = image.shape[:2]
        if width >= self._TARGET_MIN_WIDTH:
            return image
        scale = self._TARGET_MIN_WIDTH / float(width)
        resized = cv2.resize(
            image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC
        )
        return resized

    def _trim_borders(self, image: np.ndarray) -> np.ndarray:
        height, width = image.shape[:2]
        border = max(2, int(min(width, height) * 0.02))
        if border * 2 >= width or border * 2 >= height:
            return image
        return image[border : height - border, border : width - border]

    def _measure(self, image: np.ndarray) -> _AdvancedMetrics:
        height, width = image.shape[:2]
        if height == 0 or width == 0:
            return _AdvancedMetrics(width, height, 0.0, 0.0)
        pixels = image.reshape(-1)
        min_val = float(np.min(pixels))
        max_val = float(np.max(pixels))
        dynamic_range = max_val - min_val
        textured = float(np.sum(pixels < 220))
        ratio = textured / float(pixels.size)
        return _AdvancedMetrics(width, height, dynamic_range, ratio)

    def _looks_usable(self, metrics: _AdvancedMetrics) -> bool:
        return (
            metrics.dynamic_range >= 40.0
            and metrics.texture_ratio >= self._NON_ZERO_THRESHOLD
        )


class HybridReceiptImagePreprocessor(ReceiptImagePreprocessor):
    """Attempts an advanced OpenCV pass before falling back to the default pipeline."""

    def __init__(
        self,
        advanced: AdvancedReceiptImageProcessor,
        fallback: ReceiptImagePreprocessor,
        reporter: StepReporter | None = None,
    ) -> None:
        self._advanced = advanced
        self._fallback = fallback
        self._report = reporter or noop_reporter

    def preprocess(self, image: ReceiptImage) -> bytes:
        processed = self._advanced.preprocess(image)
        if processed is not None:
            self._report("preprocess", "Using OpenCV-enhanced image for OCR")
            return processed.bytes
        self._report("preprocess", "Falling back to default Pillow preprocessing")
        return self._fallback.preprocess(image)
