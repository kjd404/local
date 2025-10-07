from __future__ import annotations

import json
import subprocess
from pathlib import Path
from typing import Any

import pytest

from yong.ocr.binary_service import BinaryOcrService
from yong.ocr.errors import OcrProcessingError
from yong.ocr.interfaces import ReceiptImagePreprocessor
from yong.ocr.receipt_image import ReceiptImage


class FakePreprocessor(ReceiptImagePreprocessor):
    def __init__(self, payload: bytes) -> None:
        self._payload = payload

    def preprocess(self, image: ReceiptImage) -> bytes:
        assert image.bytes == b"input"
        return self._payload


class StubProcess:
    def __init__(self, returncode: int, stdout: str = "", stderr: str = "") -> None:
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


def _make_service(
    *,
    process: StubProcess,
    payload: bytes = b"processed",
) -> tuple[BinaryOcrService, dict[str, Any]]:
    calls: dict[str, Any] = {}

    def runner(args, *, input, text, capture_output, env=None, timeout=None):
        calls["args"] = args
        calls["input"] = input
        calls["text"] = text
        calls["capture_output"] = capture_output
        calls["env"] = env
        calls["timeout"] = timeout
        return process

    service = BinaryOcrService(
        FakePreprocessor(payload),
        binary_path=Path("/tmp/backend"),
        runner=runner,
        locale="en_US",
    )
    return service, calls


def test_extract_text_success() -> None:
    response = json.dumps({"text": "hello world", "warnings": ["note"]})
    service, calls = _make_service(process=StubProcess(0, stdout=response))

    result = service.extract_text(ReceiptImage(b"input"))

    assert result.text == "hello world"
    assert calls["args"][0].endswith("backend")
    payload = json.loads(calls["input"])
    assert payload["locale"] == "en_US"
    assert payload["minimum_confidence"] == pytest.approx(0.3)
    assert payload["recognition_level"] == "accurate"
    assert service.backend_name in {"vision", "paddle", "unknown"}


@pytest.mark.parametrize("code", [2, 3, 4, 7])
def test_extract_text_raises_on_failure(code: int) -> None:
    process = StubProcess(code, stderr="failure")
    service, _ = _make_service(process=process)

    with pytest.raises(OcrProcessingError):
        service.extract_text(ReceiptImage(b"input"))


def test_extract_text_propagates_timeout() -> None:
    def runner(*args, **kwargs):
        raise subprocess.TimeoutExpired(cmd="backend", timeout=5)

    service = BinaryOcrService(
        FakePreprocessor(b"processed"),
        binary_path=Path("/tmp/backend"),
        runner=runner,
    )

    with pytest.raises(OcrProcessingError) as exc:
        service.extract_text(ReceiptImage(b"input"))

    assert "timed out" in str(exc.value)
