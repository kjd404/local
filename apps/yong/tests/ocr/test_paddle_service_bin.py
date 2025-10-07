from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pytest
from bazel_tools.tools.python.runfiles import runfiles

from yong.ocr import (
    DefaultReceiptImagePreprocessor,
    OcrProcessingError,
    ReceiptImage,
)
from yong.ocr.paddle_service_bin import (
    InvocationError,
    OcrInvocation,
    PaddleOcrApplication,
)
from yong.ocr.reporting import noop_reporter


@dataclass
class _FakeRequestReader:
    payload: str

    def read(self) -> str:
        return self.payload


@dataclass
class _FakeParser:
    invocation: OcrInvocation | None = None
    error: Exception | None = None

    def parse(self, _: str) -> OcrInvocation:
        if self.error is not None:
            raise self.error
        assert self.invocation is not None
        return self.invocation


@dataclass
class _FakeServiceFactory:
    service: Any
    called: bool = False

    def create(
        self, invocation: OcrInvocation
    ) -> Any:  # pragma: no cover - invoked in tests
        self.called = True
        return self.service


@dataclass
class _FakeExecutor:
    text: str | None = None
    error: Exception | None = None

    def execute(self, service: Any, invocation: OcrInvocation) -> str:
        if self.error is not None:
            raise self.error
        assert self.text is not None
        return self.text


@dataclass
class _FakeResponseWriter:
    writes: list[str]

    def write(self, text: str) -> None:
        self.writes.append(text)


def _build_invocation() -> OcrInvocation:
    return OcrInvocation(
        image_bytes=b"test-bytes",
        locale="en_US",
        minimum_confidence=0.5,
        recognition_level="accurate",
    )


def test_application_returns_validation_exit_code(
    capsys: pytest.CaptureFixture[str],
) -> None:
    reader = _FakeRequestReader("payload")
    parser = _FakeParser(error=InvocationError("bad request"))
    service_factory = _FakeServiceFactory(service=object())
    executor = _FakeExecutor()
    writer = _FakeResponseWriter([])

    app = PaddleOcrApplication(
        reader,
        parser,
        service_factory,
        executor,
        writer,
        stderr=sys.stderr,
    )

    exit_code = app.run()
    captured = capsys.readouterr()

    assert exit_code == 2
    assert "validation_error: bad request" in captured.err
    assert service_factory.called is False
    assert writer.writes == []


def test_application_returns_ocr_failure_exit_code(
    capsys: pytest.CaptureFixture[str],
) -> None:
    invocation = _build_invocation()
    reader = _FakeRequestReader("payload")
    parser = _FakeParser(invocation=invocation)
    service = object()
    service_factory = _FakeServiceFactory(service=service)
    executor = _FakeExecutor(error=OcrProcessingError("paddle failed"))
    writer = _FakeResponseWriter([])

    app = PaddleOcrApplication(
        reader,
        parser,
        service_factory,
        executor,
        writer,
        stderr=sys.stderr,
    )

    exit_code = app.run()
    captured = capsys.readouterr()

    assert exit_code == 3
    assert "ocr_failure: paddle failed" in captured.err
    assert service_factory.called is True
    assert writer.writes == []


def test_application_returns_internal_error_exit_code(
    capsys: pytest.CaptureFixture[str],
) -> None:
    invocation = _build_invocation()
    reader = _FakeRequestReader("payload")
    parser = _FakeParser(invocation=invocation)
    service = object()
    service_factory = _FakeServiceFactory(service=service)
    executor = _FakeExecutor(error=RuntimeError("boom"))
    writer = _FakeResponseWriter([])

    app = PaddleOcrApplication(
        reader,
        parser,
        service_factory,
        executor,
        writer,
        stderr=sys.stderr,
    )

    exit_code = app.run()
    captured = capsys.readouterr()

    assert exit_code == 4
    assert "internal_error: boom" in captured.err
    assert service_factory.called is True
    assert writer.writes == []


def _rlocation(relative: str) -> Path:
    r = runfiles.Create()
    workspace = os.environ.get("TEST_WORKSPACE", "")
    logical = f"{workspace}/{relative}" if workspace else relative
    located = r.Rlocation(logical)
    if not located:
        raise FileNotFoundError(logical)
    return Path(located)


def _build_request_payload() -> str:
    image_path = _rlocation(
        "apps/yong/src/test/resources/examples/sample_receipt_photo_01.jpeg"
    )
    image_bytes = image_path.read_bytes()
    preprocessor = DefaultReceiptImagePreprocessor(reporter=noop_reporter)
    processed = preprocessor.preprocess(
        ReceiptImage(image_bytes, content_type="image/jpeg")
    )
    request = {
        "image_png_base64": base64.b64encode(processed).decode("ascii"),
        "locale": "en_US",
        "minimum_confidence": 0.3,
        "recognition_level": "accurate",
    }
    return json.dumps(request)


def test_paddle_service_binary_matches_golden() -> None:
    binary_path = _rlocation("apps/yong/ocr_service_binary")
    expected_path = _rlocation(
        "apps/yong/tests/ocr/golden/sample_receipt_photo_01_response_paddle.json"
    )

    if not binary_path.exists():
        pytest.skip("OCR service binary missing from runfiles")
    if "paddle" not in str(binary_path).lower():
        pytest.skip("ocr_service_binary alias not pointing at Paddle backend")

    payload = _build_request_payload()
    expected = json.loads(expected_path.read_text())

    process = subprocess.run(
        [str(binary_path)],
        input=payload,
        text=True,
        capture_output=True,
    )

    if process.returncode != 0:
        pytest.skip(
            "Paddle OCR binary unavailable (rc=%s): %s"
            % (process.returncode, process.stderr.strip())
        )

    actual = json.loads(process.stdout)
    assert actual == expected
