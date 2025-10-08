"""Value objects used throughout the Plutary receipt pipeline."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, List, Optional
from uuid import UUID, uuid4


class ReceiptProcessingStatus(str, Enum):
    PENDING = "PENDING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


@dataclass(frozen=True)
class ReceiptTransaction:
    merchant: Optional[str]
    total_amount_cents: Optional[int]
    currency: Optional[str]
    occurred_at: Optional[datetime] = None


@dataclass(frozen=True)
class ReceiptLineItem:
    line_number: int
    description: str
    quantity: Optional[float] = None
    amount_cents: Optional[int] = None


@dataclass(frozen=True)
class ParsedReceipt:
    transaction: Optional[ReceiptTransaction]
    line_items: List[ReceiptLineItem] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        transaction_dict: dict[str, Any] | None = None
        if self.transaction is not None:
            transaction_dict = {
                "merchant": self.transaction.merchant,
                "totalAmountCents": self.transaction.total_amount_cents,
                "currency": self.transaction.currency,
                "occurredAt": self.transaction.occurred_at.isoformat()
                if self.transaction.occurred_at
                else None,
            }
        items = [
            {
                "lineNumber": item.line_number,
                "description": item.description,
                "quantity": item.quantity,
                "amountCents": item.amount_cents,
            }
            for item in self.line_items
        ]
        return {
            "transaction": transaction_dict,
            "lineItems": items,
        }


@dataclass(frozen=True)
class ReceiptIngestionRecord:
    id: UUID
    account_external_id: str
    client_receipt_id: str
    captured_at: datetime
    status: ReceiptProcessingStatus
    ocr_text: str
    failure_reason: Optional[str] = None

    @classmethod
    def create(
        cls,
        account_external_id: str,
        client_receipt_id: str,
        *,
        captured_at: datetime,
        ocr_text: str,
        status: ReceiptProcessingStatus,
        failure_reason: str | None = None,
    ) -> "ReceiptIngestionRecord":
        return cls(
            id=uuid4(),
            account_external_id=account_external_id,
            client_receipt_id=client_receipt_id,
            captured_at=captured_at,
            status=status,
            ocr_text=ocr_text,
            failure_reason=failure_reason,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": str(self.id),
            "accountExternalId": self.account_external_id,
            "clientReceiptId": self.client_receipt_id,
            "capturedAt": self.captured_at.isoformat(),
            "status": self.status.value,
            "failureReason": self.failure_reason,
            "ocrText": self.ocr_text,
        }


@dataclass(frozen=True)
class ReceiptProcessingResult:
    record: ReceiptIngestionRecord
    parsed_receipt: ParsedReceipt

    def to_dict(self) -> dict[str, Any]:
        base = self.record.to_dict()
        base["parsed"] = self.parsed_receipt.to_dict()
        return base
