from __future__ import annotations

import base64
import binascii
import json
import sys
from dataclasses import dataclass
from typing import Any, Callable, TextIO

from paddleocr import PaddleOCR  # type: ignore[import]

from yong.ocr import (
    DefaultReceiptImagePreprocessor,
    HybridReceiptImagePreprocessor,
    OcrProcessingError,
    OpenCvReceiptImageProcessor,
    PaddleReceiptOcrService,
    ReceiptImage,
)
from yong.ocr.reporting import noop_reporter


@dataclass
class OcrInvocation:
    image_bytes: bytes
    locale: str
    minimum_confidence: float
    recognition_level: str


class InvocationError(RuntimeError):
    """Raised when the OCR invocation payload is malformed."""


def _paddle_language(locale: str) -> str:
    # PaddleOCR expects language identifiers like "en" or "en_dict".
    if not locale:
        return "en"
    normalized = locale.lower().replace("-", "_")
    return normalized.split("_")[0] or "en"


class RequestReader:
    """Reads the raw OCR invocation payload from an input stream."""

    def __init__(self, stream: TextIO) -> None:
        self._stream = stream

    def read(self) -> str:
        payload = self._stream.read()
        if not payload:
            raise InvocationError("request body is empty")
        return payload


class InvocationParser:
    """Validates and converts a raw payload into an `OcrInvocation`."""

    def parse(self, payload: str) -> OcrInvocation:
        try:
            raw = json.loads(payload)
        except json.JSONDecodeError as exc:  # pragma: no cover - schema validated below
            raise InvocationError(f"malformed JSON: {exc.msg}") from exc

        if not isinstance(raw, dict):
            raise InvocationError("request body must be a JSON object")

        try:
            encoded = raw["image_png_base64"]
        except KeyError as exc:
            raise InvocationError("image_png_base64 is required") from exc

        if not isinstance(encoded, str) or not encoded.strip():
            raise InvocationError("image_png_base64 must be a non-empty base64 string")

        try:
            image_bytes = base64.b64decode(encoded, validate=True)
        except (ValueError, binascii.Error) as exc:
            raise InvocationError("image_png_base64 is not valid base64") from exc

        locale = raw.get("locale") or "en_US"
        if not isinstance(locale, str):
            raise InvocationError("locale must be a string")

        minimum_confidence = raw.get("minimum_confidence", 0.3)
        if not isinstance(minimum_confidence, (int, float)):
            raise InvocationError("minimum_confidence must be numeric")
        minimum_confidence = float(minimum_confidence)
        if not 0.0 <= minimum_confidence <= 1.0:
            raise InvocationError("minimum_confidence must be between 0.0 and 1.0")

        recognition_level = raw.get("recognition_level", "accurate")
        if not isinstance(recognition_level, str):
            raise InvocationError("recognition_level must be a string")

        return OcrInvocation(
            image_bytes=image_bytes,
            locale=locale,
            minimum_confidence=minimum_confidence,
            recognition_level=recognition_level,
        )


class OcrServiceFactory:
    """Builds `PaddleReceiptOcrService` instances for a given invocation."""

    def __init__(
        self,
        *,
        paddle_ocr_constructor: Callable[[str], PaddleOCR] | None = None,
    ) -> None:
        self._paddle_ocr_constructor = (
            paddle_ocr_constructor or self._default_constructor
        )

    def create(self, invocation: OcrInvocation) -> PaddleReceiptOcrService:
        engine = self._paddle_ocr_constructor(_paddle_language(invocation.locale))
        fallback = DefaultReceiptImagePreprocessor(reporter=noop_reporter)
        preprocessor = HybridReceiptImagePreprocessor(
            OpenCvReceiptImageProcessor(reporter=noop_reporter),
            fallback,
            reporter=noop_reporter,
        )
        return PaddleReceiptOcrService(
            engine,
            preprocessor,
            rotation_angles=(0, 90, 180, 270),
            step_reporter=noop_reporter,
        )

    @staticmethod
    def _default_constructor(language: str) -> PaddleOCR:
        try:
            return PaddleOCR(lang=language, use_angle_cls=False, show_log=False)
        except (TypeError, ValueError) as exc:
            if "show_log" not in str(exc):
                raise
            return PaddleOCR(lang=language, use_angle_cls=False)


class OcrExecutor:
    """Runs OCR using the provided service and returns the recognized text."""

    def __init__(self, *, content_type: str = "image/png") -> None:
        self._content_type = content_type

    def execute(
        self, service: PaddleReceiptOcrService, invocation: OcrInvocation
    ) -> str:
        receipt_image = ReceiptImage(
            invocation.image_bytes, content_type=self._content_type
        )
        result = service.extract_text(receipt_image)
        return result.text


class ResponseWriter:
    """Serializes OCR responses to an output stream."""

    def __init__(self, stream: TextIO) -> None:
        self._stream = stream

    def write(self, text: str) -> None:
        response = self._build_response(text)
        json.dump(response, self._stream, indent=2, sort_keys=True)
        self._stream.write("\n")

    @staticmethod
    def _build_response(text: str) -> dict[str, Any]:
        warnings: list[str] = []
        if not text.strip():
            warnings.append("no_text_detected")
        return {"text": text, "warnings": warnings}


class PaddleOcrApplication:
    """Coordinates request parsing, service execution, and response writing."""

    def __init__(
        self,
        request_reader: RequestReader,
        parser: InvocationParser,
        service_factory: OcrServiceFactory,
        executor: OcrExecutor,
        response_writer: ResponseWriter,
        *,
        stderr: TextIO,
    ) -> None:
        self._request_reader = request_reader
        self._parser = parser
        self._service_factory = service_factory
        self._executor = executor
        self._response_writer = response_writer
        self._stderr = stderr

    def run(self) -> int:
        try:
            payload = self._request_reader.read()
            invocation = self._parser.parse(payload)
        except InvocationError as exc:
            print(f"validation_error: {exc}", file=self._stderr)
            return 2

        service = self._service_factory.create(invocation)

        try:
            text = self._executor.execute(service, invocation)
        except OcrProcessingError as exc:
            print(f"ocr_failure: {exc}", file=self._stderr)
            return 3
        except (
            Exception
        ) as exc:  # pragma: no cover - defensive guard for Paddle internals
            print(f"internal_error: {exc}", file=self._stderr)
            return 4

        self._response_writer.write(text)
        return 0


def build_default_app(
    *,
    stdin: TextIO | None = None,
    stdout: TextIO | None = None,
    stderr: TextIO | None = None,
) -> PaddleOcrApplication:
    reader = RequestReader(stdin or sys.stdin)
    parser = InvocationParser()
    service_factory = OcrServiceFactory()
    executor = OcrExecutor()
    writer = ResponseWriter(stdout or sys.stdout)
    return PaddleOcrApplication(
        reader,
        parser,
        service_factory,
        executor,
        writer,
        stderr=stderr or sys.stderr,
    )


def main() -> int:
    app = build_default_app()
    return app.run()


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
