from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import pytest

from yong.ocr import OcrResult, ReceiptOcrService, ReceiptImage
from yong.receipts import (
    HeuristicReceiptParser,
    ReceiptProcessingContext,
    ReceiptProcessingPipeline,
    ReceiptProcessingStatus,
)


@dataclass
class GroundTruthFixture:
    path: Path

    @property
    def data(self) -> dict:
        return json.loads(self.path.read_text())

    @property
    def image_path(self) -> Path:
        examples_dir = self.path.parent.parent / "examples"
        return examples_dir / f"{self.path.stem}.jpeg"


class GroundTruthOcrService(ReceiptOcrService):
    def __init__(self, text: str) -> None:
        self._text = text
        self.calls: list[bytes] = []

    def extract_text(self, image: ReceiptImage) -> OcrResult:  # type: ignore[override]
        self.calls.append(image.bytes)
        return OcrResult(self._text)


FIXTURE_DIR = (
    Path(__file__).resolve().parents[2] / "src" / "test" / "resources" / "ground_truth"
)
FIXTURES = sorted(FIXTURE_DIR.glob("*.json"))
SAMPLE_EXPECTATIONS = {
    "sample_receipt_photo_02": {
        "item": "Org Spaghetti",
        "amount": 1289,
        "quantity": None,
    },
    "sample_receipt_photo_03": {
        "item": "Marble Taro",
        "amount": 370,
        "quantity": 1,
    },
    "sample_receipt_photo_04": {
        "item": "Blackberries",
        "amount": 1199,
        "quantity": 4,
    },
    "sample_receipt_photo_05": {
        "item": "Enfamil Gent",
        "amount": 5899,
        "quantity": None,
    },
}


def _parse_captured_at(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


@pytest.mark.parametrize("fixture_path", FIXTURES, ids=[p.stem for p in FIXTURES])
def test_pipeline_matches_ground_truth(fixture_path: Path) -> None:
    fixture = GroundTruthFixture(fixture_path)
    payload = fixture.data
    expected = payload["expected_transaction"]

    image_bytes = fixture.image_path.read_bytes()
    service = GroundTruthOcrService(payload["ocr_text"])
    parser = HeuristicReceiptParser()
    pipeline = ReceiptProcessingPipeline(service, parser)

    context = ReceiptProcessingContext(
        account_external_id=payload["account_external_id"],
        client_receipt_id=payload["receipt_id"],
        captured_at=_parse_captured_at(expected["occurred_at"]),
        content_type="image/jpeg",
    )

    result = pipeline.process(image_bytes, context)

    assert service.calls and service.calls[0] == image_bytes
    assert result.record.status is ReceiptProcessingStatus.COMPLETED
    assert result.record.failure_reason is None
    assert result.record.account_external_id == payload["account_external_id"]
    assert result.record.client_receipt_id == payload["receipt_id"]
    assert result.record.ocr_text.strip() == payload["ocr_text"]

    transaction = result.parsed_receipt.transaction
    assert transaction is not None, "Parser should produce a transaction summary"
    assert transaction.merchant == expected["merchant"]
    assert transaction.total_amount_cents == expected["amount_cents"]
    assert transaction.currency == expected["currency"]

    assert (
        result.parsed_receipt.to_dict()["transaction"]["totalAmountCents"]
        == expected["amount_cents"]
    )

    sample_id = fixture_path.stem
    expectation = SAMPLE_EXPECTATIONS.get(sample_id)
    if expectation:
        items = result.parsed_receipt.line_items
        assert items, f"Expected line items for {sample_id}"
        target_desc = expectation["item"].lower()
        match = next(
            (item for item in items if target_desc in item.description.lower()),
            None,
        )
        assert match is not None, f"Could not find expected item '{target_desc}'"
        assert match.amount_cents == expectation["amount"], match
        quantity = expectation["quantity"]
        if quantity is not None:
            assert match.quantity == quantity


def test_pipeline_handles_split_total_lines() -> None:
    text = """
    Domino's Pizza #7157
    Total
    $27.85
    Amount Tendered
    $27.85
    Balance Due
    $0.00
    """

    service = GroundTruthOcrService(text)
    parser = HeuristicReceiptParser()
    pipeline = ReceiptProcessingPipeline(service, parser)

    context = ReceiptProcessingContext(
        account_external_id="acct",
        client_receipt_id="receipt-001",
        captured_at=datetime.now(timezone.utc),
        content_type="image/jpeg",
    )

    result = pipeline.process(b"bytes", context)

    assert service.calls and service.calls[0] == b"bytes"
    assert result.record.status is ReceiptProcessingStatus.COMPLETED
    assert result.record.failure_reason is None
    assert result.parsed_receipt.transaction.total_amount_cents == 2785
