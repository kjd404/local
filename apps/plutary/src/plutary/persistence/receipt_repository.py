"""Persistence adapter for processed receipt ingestions."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable
from uuid import uuid4
import hashlib

from plutary.receipts import (
    ParsedReceipt,
    ReceiptProcessingContext,
    ReceiptProcessingResult,
)

from .database import Database


@dataclass()
class ReceiptPersistenceService:
    """Persists processed receipts and associated artifacts."""

    database: Database

    def persist(
        self,
        *,
        result: ReceiptProcessingResult,
        context: ReceiptProcessingContext,
        image_bytes: bytes,
    ) -> None:
        record = result.record
        parsed = result.parsed_receipt

        checksum = hashlib.sha256(image_bytes).hexdigest()
        byte_size = len(image_bytes)

        with self.database.connect() as conn:
            with conn.cursor() as cur:
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
                    ON CONFLICT (id) DO NOTHING
                    """,
                    (
                        str(record.id),
                        record.client_receipt_id,
                        record.account_external_id,
                        record.ocr_text,
                        record.captured_at,
                        record.status.value,
                        record.failure_reason,
                    ),
                )
                cur.execute(
                    """
                    INSERT INTO receipt_images (
                        receipt_ingestion_id,
                        content_type,
                        image_bytes,
                        byte_size,
                        checksum
                    ) VALUES (%s, %s, %s, %s, %s)
                    ON CONFLICT (receipt_ingestion_id) DO NOTHING
                    """,
                    (
                        str(record.id),
                        context.content_type,
                        image_bytes,
                        byte_size,
                        checksum,
                    ),
                )

                self._insert_transaction(cur, record.id, parsed, context)

    def _insert_transaction(
        self,
        cursor,
        ingestion_id,
        parsed: ParsedReceipt,
        context: ReceiptProcessingContext,
    ) -> None:
        transaction = parsed.transaction
        if transaction is None:
            return
        if transaction.total_amount_cents is None:
            return
        merchant = transaction.merchant
        if not merchant:
            return
        currency = transaction.currency or "USD"
        occurred_at = transaction.occurred_at or context.captured_at

        cursor.execute(
            """
            INSERT INTO receipt_transactions (
                receipt_ingestion_id,
                merchant,
                total_amount_cents,
                currency,
                occurred_at
            ) VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (receipt_ingestion_id) DO UPDATE SET
                merchant = EXCLUDED.merchant,
                total_amount_cents = EXCLUDED.total_amount_cents,
                currency = EXCLUDED.currency,
                occurred_at = EXCLUDED.occurred_at
            """,
            (
                str(ingestion_id),
                merchant,
                transaction.total_amount_cents,
                currency,
                occurred_at,
            ),
        )

        self._insert_line_items(cursor, ingestion_id, parsed.line_items)

    def _insert_line_items(self, cursor, ingestion_id, line_items: Iterable) -> None:
        receipt_transaction_id = str(ingestion_id)
        cursor.execute(
            "DELETE FROM receipt_line_items WHERE receipt_transaction_id = %s",
            (receipt_transaction_id,),
        )
        for item in line_items:
            quantity = None
            if item.quantity is not None:
                quantity = int(item.quantity)
            cursor.execute(
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
                    str(uuid4()),
                    receipt_transaction_id,
                    item.line_number,
                    item.description,
                    quantity,
                    item.amount_cents,
                ),
            )


__all__ = ["ReceiptPersistenceService"]
