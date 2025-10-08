from __future__ import annotations

import hashlib
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

from plutary.cli import receipt_cli
from plutary.receipts import (
    ParsedReceipt,
    ReceiptIngestionRecord,
    ReceiptLineItem,
    ReceiptProcessingContext,
    ReceiptProcessingResult,
    ReceiptProcessingStatus,
    ReceiptTransaction,
)

from ..util import (
    ReceiptIngestionRow,
    ReceiptLineItemRow,
    ReceiptTransactionRow,
    RecordFactory,
)


@dataclass(slots=True)
class StubPipeline:
    result: ReceiptProcessingResult
    calls: list[tuple[bytes, ReceiptProcessingContext]]

    def __init__(self, result: ReceiptProcessingResult) -> None:
        self.result = result
        self.calls = []

    def process(
        self, image_bytes: bytes, context: ReceiptProcessingContext
    ) -> ReceiptProcessingResult:
        self.calls.append((image_bytes, context))
        return self.result


def test_cli_persists_new_receipt(
    tmp_path: Path,
    monkeypatch,
    cli_runner,
    record_factory: RecordFactory,
) -> None:
    ingestion_id = UUID("00000000-0000-0000-0000-000000000123")
    captured_at = datetime(2024, 1, 2, 3, 4, 5, tzinfo=timezone.utc)
    transaction = ReceiptTransaction(
        merchant="New Merchant",
        total_amount_cents=2599,
        currency="USD",
        occurred_at=datetime(2024, 1, 2, 4, 0, tzinfo=timezone.utc),
    )
    parsed = ParsedReceipt(
        transaction=transaction,
        line_items=[
            ReceiptLineItem(
                line_number=1,
                description="Coffee",
                quantity=1,
                amount_cents=2599,
            ),
        ],
    )
    record = ReceiptIngestionRecord(
        id=ingestion_id,
        account_external_id="acct-new",
        client_receipt_id="client-new",
        captured_at=captured_at,
        status=ReceiptProcessingStatus.COMPLETED,
        ocr_text="Total $25.99",
        failure_reason=None,
    )
    stub_result = ReceiptProcessingResult(record=record, parsed_receipt=parsed)
    stub_pipeline = StubPipeline(stub_result)
    monkeypatch.setattr(
        receipt_cli,
        "_create_pipeline",
        lambda *, args, reporter: stub_pipeline,
    )

    image_path = tmp_path / "test-receipt.png"
    image_bytes = b"fake-receipt-bytes"
    image_path.write_bytes(image_bytes)

    cli_result = cli_runner(
        [
            "--image",
            str(image_path),
            "--account",
            record.account_external_id,
            "--client-receipt-id",
            record.client_receipt_id,
        ]
    )

    assert cli_result.exit_code == 0
    assert "receipt persisted" in cli_result.stdout
    assert len(stub_pipeline.calls) == 1

    call_bytes, call_context = stub_pipeline.calls[0]
    assert call_bytes == image_bytes
    assert call_context.account_external_id == record.account_external_id
    assert call_context.client_receipt_id == record.client_receipt_id
    assert call_context.content_type == "image/png"

    ingestion_row = record_factory.fetch_receipt_ingestion(ingestion_id)
    assert ingestion_row is not None
    assert ingestion_row["account_external_id"] == record.account_external_id
    assert ingestion_row["ocr_text"] == record.ocr_text
    assert ingestion_row["status"] == record.status.value

    image_row = record_factory.fetch_receipt_image(ingestion_id)
    assert image_row is not None
    expected_checksum = hashlib.sha256(image_bytes).hexdigest()
    assert image_row["byte_size"] == len(image_bytes)
    assert image_row["checksum"] == expected_checksum
    assert image_row["content_type"] == "image/png"

    transaction_row = record_factory.fetch_receipt_transaction(ingestion_id)
    assert transaction_row is not None
    assert transaction_row["merchant"] == transaction.merchant
    assert transaction_row["total_amount_cents"] == transaction.total_amount_cents
    assert transaction_row["currency"] == transaction.currency
    assert (
        transaction_row["occurred_at"].astimezone(timezone.utc)
        == transaction.occurred_at
    )

    line_items = record_factory.fetch_receipt_line_items(ingestion_id)
    assert len(line_items) == 1
    assert line_items[0]["description"] == "Coffee"
    assert line_items[0]["amount_cents"] == 2599


def test_cli_replaces_existing_transaction(
    tmp_path: Path,
    monkeypatch,
    cli_runner,
    record_factory: RecordFactory,
) -> None:
    ingestion_id = UUID("00000000-0000-0000-0000-000000000321")

    original = record_factory.create_record(
        ReceiptIngestionRow,
        overrides={
            "id": ingestion_id,
            "client_receipt_id": "client-existing",
            "account_external_id": "acct-existing",
            "ocr_text": "Old total 10.00",
            "status": ReceiptProcessingStatus.COMPLETED,
        },
    )
    record_factory.create_record(
        ReceiptTransactionRow,
        overrides={
            "receipt_ingestion_id": ingestion_id,
            "merchant": "Old Merchant",
            "total_amount_cents": 1000,
            "currency": "USD",
            "occurred_at": datetime(2023, 12, 30, tzinfo=timezone.utc),
        },
    )
    record_factory.create_record(
        ReceiptLineItemRow,
        overrides={
            "receipt_transaction_id": ingestion_id,
            "line_number": 1,
            "description": "Old item",
            "amount_cents": 1000,
        },
    )

    updated_transaction = ReceiptTransaction(
        merchant="Updated Merchant",
        total_amount_cents=1899,
        currency="CAD",
        occurred_at=datetime(2024, 1, 5, 12, 0, tzinfo=timezone.utc),
    )
    updated_parsed = ParsedReceipt(
        transaction=updated_transaction,
        line_items=[
            ReceiptLineItem(
                line_number=1,
                description="New item",
                quantity=2,
                amount_cents=1899,
            )
        ],
    )
    updated_record = ReceiptIngestionRecord(
        id=ingestion_id,
        account_external_id="acct-existing",
        client_receipt_id="client-existing",
        captured_at=original.captured_at,
        status=ReceiptProcessingStatus.COMPLETED,
        ocr_text="Updated total 18.99",
        failure_reason=None,
    )
    updated_result = ReceiptProcessingResult(
        record=updated_record,
        parsed_receipt=updated_parsed,
    )
    stub_pipeline = StubPipeline(updated_result)
    monkeypatch.setattr(
        receipt_cli,
        "_create_pipeline",
        lambda *, args, reporter: stub_pipeline,
    )

    image_path = tmp_path / "existing-receipt.png"
    image_bytes = b"updated-bytes"
    image_path.write_bytes(image_bytes)

    cli_result = cli_runner(
        [
            "--image",
            str(image_path),
            "--account",
            updated_record.account_external_id,
            "--client-receipt-id",
            updated_record.client_receipt_id,
        ]
    )

    assert cli_result.exit_code == 0
    assert "receipt persisted" in cli_result.stdout
    assert len(stub_pipeline.calls) == 1

    ingestion_row = record_factory.fetch_receipt_ingestion(ingestion_id)
    assert ingestion_row is not None
    assert ingestion_row["ocr_text"] == "Old total 10.00"

    transaction_row = record_factory.fetch_receipt_transaction(ingestion_id)
    assert transaction_row is not None
    assert transaction_row["merchant"] == updated_transaction.merchant
    assert (
        transaction_row["total_amount_cents"] == updated_transaction.total_amount_cents
    )
    assert transaction_row["currency"] == updated_transaction.currency

    line_items = record_factory.fetch_receipt_line_items(ingestion_id)
    assert len(line_items) == 1
    descriptions = [item["description"] for item in line_items]
    assert descriptions == ["New item"]
    assert line_items[0]["quantity"] == 2
    assert line_items[0]["amount_cents"] == 1899

    image_row = record_factory.fetch_receipt_image(ingestion_id)
    assert image_row is not None
    assert image_row["byte_size"] == len(image_bytes)
    assert image_row["checksum"] == hashlib.sha256(image_bytes).hexdigest()
