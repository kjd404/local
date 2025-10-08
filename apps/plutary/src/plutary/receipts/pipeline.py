"""Receipt processing pipeline orchestrating OCR and heuristic parsing."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Callable, Optional

from plutary.ocr import OcrProcessingError, ReceiptImage, ReceiptOcrService

from .models import (
    ParsedReceipt,
    ReceiptIngestionRecord,
    ReceiptProcessingResult,
    ReceiptProcessingStatus,
)
from .parser import HeuristicReceiptParser

StepReporter = Callable[[str, str], None]


def _noop_reporter(
    stage: str, message: str
) -> None:  # pragma: no cover - trivial helper
    del stage, message


@dataclass(frozen=True)
class ReceiptProcessingContext:
    account_external_id: str
    client_receipt_id: str
    captured_at: datetime
    content_type: Optional[str]


class ReceiptProcessingPipeline:
    """Coordinates preprocessing, OCR, and heuristic parsing for a receipt image."""

    def __init__(
        self,
        ocr_service: ReceiptOcrService,
        parser: HeuristicReceiptParser,
        *,
        step_reporter: Optional[StepReporter] = None,
    ) -> None:
        self._ocr_service = ocr_service
        self._parser = parser
        self._report = step_reporter or _noop_reporter

    def process(
        self, image_bytes: bytes, context: ReceiptProcessingContext
    ) -> ReceiptProcessingResult:
        if not image_bytes:
            raise ValueError("image_bytes must not be empty")

        self._report("pipeline", "Starting receipt ingestion pipeline")
        receipt_image = ReceiptImage(image_bytes, context.content_type)
        try:
            ocr_result = self._ocr_service.extract_text(receipt_image)
        except OcrProcessingError as exc:
            self._report("pipeline", f"OCR failed: {exc}")
            record = ReceiptIngestionRecord.create(
                account_external_id=context.account_external_id,
                client_receipt_id=context.client_receipt_id,
                captured_at=context.captured_at,
                ocr_text="",
                status=ReceiptProcessingStatus.FAILED,
                failure_reason=str(exc),
            )
            parsed = ParsedReceipt(transaction=None, line_items=[])
            return ReceiptProcessingResult(record=record, parsed_receipt=parsed)

        text = ocr_result.text
        if not text.strip():
            self._report("pipeline", "OCR text empty; marking ingestion as failed")
            record = ReceiptIngestionRecord.create(
                account_external_id=context.account_external_id,
                client_receipt_id=context.client_receipt_id,
                captured_at=context.captured_at,
                ocr_text=text,
                status=ReceiptProcessingStatus.FAILED,
                failure_reason="OCR produced empty text.",
            )
            parsed = ParsedReceipt(transaction=None, line_items=[])
            return ReceiptProcessingResult(record=record, parsed_receipt=parsed)

        self._report("pipeline", "Running heuristic parser on OCR output")
        parsed = self._parser.parse(text)
        status = ReceiptProcessingStatus.COMPLETED
        failure_reason = None
        if parsed.transaction and parsed.transaction.total_amount_cents is None:
            status = ReceiptProcessingStatus.PENDING
            failure_reason = "Total amount not detected; manual review required."
            self._report("pipeline", failure_reason)
        else:
            self._report("pipeline", "Receipt parsing completed successfully")

        record = ReceiptIngestionRecord.create(
            account_external_id=context.account_external_id,
            client_receipt_id=context.client_receipt_id,
            captured_at=context.captured_at,
            ocr_text=text,
            status=status,
            failure_reason=failure_reason,
        )
        return ReceiptProcessingResult(record=record, parsed_receipt=parsed)
