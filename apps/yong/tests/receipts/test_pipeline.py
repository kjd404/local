from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Tuple

from yong.ocr import OcrResult, ReceiptOcrService
from yong.receipts import (
    HeuristicReceiptParser,
    ReceiptProcessingContext,
    ReceiptProcessingPipeline,
    ReceiptProcessingStatus,
)


class FakeOcrService(ReceiptOcrService):
    def __init__(self, text: str) -> None:
        self._text = text
        self.calls: List[bytes] = []

    def extract_text(self, image) -> OcrResult:  # type: ignore[override]
        self.calls.append(image.bytes)
        return OcrResult(self._text)


def test_pipeline_emits_summary_and_steps() -> None:
    steps: List[Tuple[str, str]] = []

    def reporter(stage: str, message: str) -> None:
        steps.append((stage, message))

    ocr_service = FakeOcrService("Cafe\nTOTAL 15.99")
    parser = HeuristicReceiptParser()
    pipeline = ReceiptProcessingPipeline(ocr_service, parser, step_reporter=reporter)
    context = ReceiptProcessingContext(
        account_external_id="acct-123",
        client_receipt_id="receipt-1",
        captured_at=datetime.now(timezone.utc),
        content_type="image/png",
    )

    result = pipeline.process(b"fake-bytes", context)

    assert result.record.status is ReceiptProcessingStatus.COMPLETED
    assert result.record.ocr_text.strip() == "Cafe\nTOTAL 15.99"
    assert result.parsed_receipt.transaction is not None
    assert result.parsed_receipt.transaction.total_amount_cents == 1599
    assert any(stage == "pipeline" for stage, _ in steps)


def test_pipeline_marks_failure_when_ocr_empty() -> None:
    ocr_service = FakeOcrService("")
    parser = HeuristicReceiptParser()
    pipeline = ReceiptProcessingPipeline(ocr_service, parser)
    context = ReceiptProcessingContext(
        account_external_id="acct-123",
        client_receipt_id="receipt-2",
        captured_at=datetime.now(timezone.utc),
        content_type="image/png",
    )

    result = pipeline.process(b"fake-bytes", context)

    assert result.record.status is ReceiptProcessingStatus.FAILED
    assert result.record.failure_reason is not None
