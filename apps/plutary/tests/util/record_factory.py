"""Utilities for creating and inspecting Plutary persistence records in tests."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Type, TypeVar
from uuid import UUID, uuid4

import psycopg
from psycopg.rows import dict_row


@dataclass(frozen=True)
class ReceiptIngestionRow:
    id: UUID
    client_receipt_id: str
    account_external_id: str
    ocr_text: str
    captured_at: datetime
    status: str
    failure_reason: Optional[str]


@dataclass(frozen=True)
class ReceiptTransactionRow:
    receipt_ingestion_id: UUID
    merchant: str
    total_amount_cents: int
    currency: str
    occurred_at: Optional[datetime]


@dataclass(frozen=True)
class ReceiptLineItemRow:
    id: UUID
    receipt_transaction_id: UUID
    line_number: int
    description: str
    quantity: Optional[int]
    amount_cents: Optional[int]


RowType = TypeVar(
    "RowType",
    ReceiptIngestionRow,
    ReceiptTransactionRow,
    ReceiptLineItemRow,
)


class RecordFactory:
    """Persists Plutary receipt records with sensible defaults for tests."""

    def __init__(self, connection: psycopg.Connection) -> None:
        self._connection = connection

    def create_record(
        self,
        model_cls: Type[RowType],
        overrides: Optional[Dict[str, Any]] = None,
    ) -> RowType:
        overrides = overrides or {}
        if model_cls is ReceiptIngestionRow:
            return self._create_ingestion(overrides)
        if model_cls is ReceiptTransactionRow:
            return self._create_transaction(overrides)
        if model_cls is ReceiptLineItemRow:
            return self._create_line_item(overrides)
        raise ValueError(f"Unsupported model class: {model_cls!r}")

    def fetch_receipt_ingestion(
        self, receipt_ingestion_id: UUID | str
    ) -> Optional[Dict[str, Any]]:
        with self._connection.cursor(row_factory=dict_row) as cur:
            cur.execute(
                "SELECT * FROM receipt_ingestions WHERE id = %s",
                (str(receipt_ingestion_id),),
            )
            return cur.fetchone()

    def fetch_receipt_image(
        self, receipt_ingestion_id: UUID | str
    ) -> Optional[Dict[str, Any]]:
        with self._connection.cursor(row_factory=dict_row) as cur:
            cur.execute(
                "SELECT * FROM receipt_images WHERE receipt_ingestion_id = %s",
                (str(receipt_ingestion_id),),
            )
            return cur.fetchone()

    def fetch_receipt_transaction(
        self, receipt_ingestion_id: UUID | str
    ) -> Optional[Dict[str, Any]]:
        with self._connection.cursor(row_factory=dict_row) as cur:
            cur.execute(
                "SELECT * FROM receipt_transactions WHERE receipt_ingestion_id = %s",
                (str(receipt_ingestion_id),),
            )
            return cur.fetchone()

    def fetch_receipt_line_items(
        self, receipt_transaction_id: UUID | str
    ) -> list[Dict[str, Any]]:
        with self._connection.cursor(row_factory=dict_row) as cur:
            cur.execute(
                "SELECT * FROM receipt_line_items WHERE receipt_transaction_id = %s ORDER BY line_number",
                (str(receipt_transaction_id),),
            )
            rows = cur.fetchall()
        return rows or []

    # Internal helpers -------------------------------------------------

    def _create_ingestion(self, overrides: Dict[str, Any]) -> ReceiptIngestionRow:
        captured_at = overrides.get("captured_at", datetime.now(timezone.utc))
        status = overrides.get("status", "COMPLETED")
        if hasattr(status, "value"):
            status = getattr(status, "value")

        record = ReceiptIngestionRow(
            id=overrides.get("id", uuid4()),
            client_receipt_id=overrides.get("client_receipt_id", f"client-{uuid4()}"),
            account_external_id=overrides.get("account_external_id", "acct-test"),
            ocr_text=overrides.get("ocr_text", "sample ocr text"),
            captured_at=_ensure_timezone_aware(captured_at),
            status=status,
            failure_reason=overrides.get("failure_reason"),
        )

        with self._connection.cursor() as cur:
            cur.execute(
                """
                INSERT INTO receipt_ingestions (
                    id,
                    client_receipt_id,
                    account_external_id,
                    ocr_text,
                    captured_at,
                    status,
                    failure_reason
                ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    str(record.id),
                    record.client_receipt_id,
                    record.account_external_id,
                    record.ocr_text,
                    record.captured_at,
                    record.status,
                    record.failure_reason,
                ),
            )
        return record

    def _create_transaction(self, overrides: Dict[str, Any]) -> ReceiptTransactionRow:
        ingestion = overrides.get("receipt_ingestion_id")
        if ingestion is None:
            ingestion_record = self._create_ingestion({})
            ingestion_id = ingestion_record.id
        else:
            ingestion_id = _coerce_uuid(ingestion, attr="id")

        record = ReceiptTransactionRow(
            receipt_ingestion_id=ingestion_id,
            merchant=overrides.get("merchant", "Test Merchant"),
            total_amount_cents=overrides.get("total_amount_cents", 1234),
            currency=overrides.get("currency", "USD"),
            occurred_at=overrides.get("occurred_at", datetime.now(timezone.utc)),
        )

        with self._connection.cursor() as cur:
            cur.execute(
                """
                INSERT INTO receipt_transactions (
                    receipt_ingestion_id,
                    merchant,
                    total_amount_cents,
                    currency,
                    occurred_at
                ) VALUES (%s, %s, %s, %s, %s)
                """,
                (
                    str(record.receipt_ingestion_id),
                    record.merchant,
                    record.total_amount_cents,
                    record.currency,
                    record.occurred_at,
                ),
            )
        return record

    def _create_line_item(self, overrides: Dict[str, Any]) -> ReceiptLineItemRow:
        transaction = overrides.get("receipt_transaction_id")
        if transaction is None:
            transaction_record = self._create_transaction({})
            transaction_id = transaction_record.receipt_ingestion_id
        else:
            transaction_id = _coerce_uuid(transaction, attr="receipt_ingestion_id")

        record = ReceiptLineItemRow(
            id=overrides.get("id", uuid4()),
            receipt_transaction_id=transaction_id,
            line_number=overrides.get("line_number", 1),
            description=overrides.get("description", "Line Item"),
            quantity=_coerce_optional_int(overrides.get("quantity")),
            amount_cents=overrides.get("amount_cents"),
        )

        with self._connection.cursor() as cur:
            cur.execute(
                """
                INSERT INTO receipt_line_items (
                    id,
                    receipt_transaction_id,
                    line_number,
                    description,
                    quantity,
                    amount_cents
                ) VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (
                    str(record.id),
                    str(record.receipt_transaction_id),
                    record.line_number,
                    record.description,
                    record.quantity,
                    record.amount_cents,
                ),
            )
        return record


def _coerce_uuid(value: Any, *, attr: str) -> UUID:
    if isinstance(value, UUID):
        return value
    if hasattr(value, attr):
        candidate = getattr(value, attr)
        if isinstance(candidate, UUID):
            return candidate
        return UUID(str(candidate))
    return UUID(str(value))


def _coerce_optional_int(value: Any) -> Optional[int]:
    if value is None:
        return None
    return int(value)


def _ensure_timezone_aware(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value
