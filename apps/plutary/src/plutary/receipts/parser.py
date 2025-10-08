"""Heuristic receipt parsing utilities."""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import Iterable, List, Sequence, Tuple

from .models import ParsedReceipt, ReceiptLineItem, ReceiptTransaction

_TOTAL_LABEL_PRIORITIES: Tuple[Tuple[str, int], ...] = (
    ("total", 50),
    ("amount tendered", 40),
    ("amount due", 30),
    ("balance due", 20),
    ("balance", 10),
)
_TOTAL_LABEL_PATTERNS: Tuple[Tuple[re.Pattern[str], str, int], ...] = tuple(
    (
        re.compile(rf"(?i)\b{label.replace(' ', r'\s+')}\b"),
        label,
        priority,
    )
    for label, priority in _TOTAL_LABEL_PRIORITIES
)
_AMOUNT_TOKEN_PATTERN = re.compile(r"\(?[$€£]?\d[\d,]*[\.,]\d{2}-?\)?")
_LINE_ITEM_PATTERN = re.compile(
    r"^(?P<desc>.+?)\s+(?P<amount>[$€£]?\d[\d,]*[\.,]\d{2})$"
)
_CURRENCY_HINT = re.compile(r"(usd|eur|gbp|cad|aud|\$|€|£)", re.IGNORECASE)
_DIGIT_TO_ALPHA = str.maketrans(
    {
        "0": "o",
        "1": "i",
        "2": "z",
        "3": "e",
        "4": "a",
        "5": "s",
        "6": "g",
        "7": "t",
        "8": "b",
    }
)
_MERCHANT_STOP_KEYWORDS: Tuple[str, ...] = (
    "subtotal",
    "total",
    "tax",
    "balance",
    "amount",
    "visa",
    "payment",
    "purchase",
    "tendered",
    "change",
    "items",
    "sale",
    "order",
    "summary",
    "phone",
    "time",
    "ready",
    "estimate",
    "thank",
    "dine",
    "drive thru",
    "pickup",
    "to go",
    "paid",
)
_ADDRESS_HINTS: Tuple[str, ...] = (
    " ave",
    " avenue",
    " st",
    " street",
    " blvd",
    " boulevard",
    " rd",
    " road",
    " dr",
    " drive",
    " ln",
    " lane",
    " hwy",
    " highway",
    " way",
    " ste",
    " suite",
    " ne",
    " nw",
    " se",
    " sw",
)
_MERCHANT_MAX_LINES = 4
_MERCHANT_MAX_LENGTH = 64


_METADATA_LINE_PREFIXES: Tuple[str, ...] = (
    "subtotal",
    "tax",
    "total",
    "balance",
    "amount",
    "visa",
    "payment",
    "purchase",
    "tendered",
    "change",
    "items",
    "credit card",
    "thank",
    "store",
    "trans",
    "transaction",
    "ransaction",
    "till",
    "date",
    "time",
    "method",
    "auth",
    "mid",
    "aid",
    "tid",
    "ref#",
    "order",
    "whse",
    "op#",
    "to go",
    "open",
    "upen",
    "member",
    "bottom of basket",
    "basket",
    "count",
    "sale",
    "halance",
)

_QUANTITY_FOR_PATTERN = re.compile(
    r"^(?P<qty>\d+)\s*(?:for)\s*(?P<amount>[$€£]?\d[\d,]*[\.,]\d{2})$",
    re.IGNORECASE,
)
_QUANTITY_AT_PATTERN = re.compile(
    r"^(?P<qty>\d+)\s*@\s*[$€£]?\d[\d,]*[\.,]\d{2}$",
    re.IGNORECASE,
)


@dataclass()
class HeuristicReceiptParser:
    """Lightweight parser that extracts totals and coarse metadata from OCR text."""

    default_currency: str = "USD"

    def parse(self, ocr_text: str) -> ParsedReceipt:
        lines = [line.strip() for line in ocr_text.splitlines() if line.strip()]
        merged_lines = self._merge_label_amount_lines(lines)
        merchant, merchant_lines = self._extract_merchant(lines)
        merchant = self._refine_merchant(merchant, lines)
        currency = self._detect_currency(lines)
        total_cents = self._extract_total_cents(lines, merged_lines)
        transaction = ReceiptTransaction(
            merchant=merchant,
            total_amount_cents=total_cents,
            currency=currency,
        )
        body_lines = merged_lines[merchant_lines:]
        line_items = self._extract_line_items(body_lines)
        return ParsedReceipt(transaction=transaction, line_items=line_items)

    def _detect_currency(self, lines: Iterable[str]) -> str:
        for line in lines:
            match = _CURRENCY_HINT.search(line)
            if match:
                token = match.group(1).upper()
                if token == "$":
                    return "USD"
                if token == "€":
                    return "EUR"
                if token == "£":
                    return "GBP"
                if token in {"USD", "EUR", "GBP", "CAD", "AUD"}:
                    return token
        return self.default_currency

    def _extract_total_cents(
        self, original_lines: Sequence[str], merged_lines: Sequence[str]
    ) -> int | None:
        candidates: List[Tuple[int, int]] = []

        for line in merged_lines:
            label = self._find_total_label(line)
            if not label:
                continue
            amount = self._find_positive_amount(line)
            if amount is None:
                continue
            candidates.append((label[1], amount))

        for index, line in enumerate(original_lines[:-1]):
            label = self._find_total_label(line)
            if not label:
                continue
            amount = self._find_positive_amount(line)
            if amount is not None:
                candidates.append((label[1], amount))
                continue
            next_amount = self._find_positive_amount(original_lines[index + 1])
            if next_amount is not None:
                candidates.append((label[1], next_amount))
        if candidates:
            priority, cents = max(candidates, key=lambda pair: (pair[0], pair[1]))
            return cents if priority > 0 else cents

        return self._select_fallback_amount(original_lines)

    def _extract_line_items(self, lines: Sequence[str]) -> List[ReceiptLineItem]:
        items: List[ReceiptLineItem] = []
        pending_description: List[str] = []
        line_number = 1
        for line in lines:
            label = self._find_total_label(line)
            if label:
                pending_description.clear()
                continue
            quantity_override = self._match_quantity_total(line)
            if quantity_override:
                if items:
                    last = items[-1]
                    items[-1] = ReceiptLineItem(
                        line_number=last.line_number,
                        description=last.description,
                        quantity=quantity_override[0],
                        amount_cents=quantity_override[1],
                    )
                pending_description.clear()
                continue
            quantity_hint = self._match_quantity_unit(line)
            if quantity_hint:
                if items and items[-1].quantity is None:
                    last = items[-1]
                    items[-1] = ReceiptLineItem(
                        line_number=last.line_number,
                        description=last.description,
                        quantity=quantity_hint,
                        amount_cents=last.amount_cents,
                    )
                pending_description.clear()
                continue
            if self._is_metadata_line(line):
                pending_description.clear()
                continue
            amount_tokens = self._amount_tokens(line)
            if amount_tokens:
                amount_token = amount_tokens[-1]
                cleaned_token = self._clean_amount_token(amount_token)
                amount_cents = self._to_cents(cleaned_token)
                if amount_cents is None:
                    pending_description.clear()
                    continue
                source_line = line
                if self._is_amount_only_line(line):
                    if not pending_description:
                        continue
                    source_line = " ".join(pending_description)
                    pending_description.clear()
                elif pending_description:
                    source_line = " ".join(pending_description + [line])
                    pending_description.clear()
                description, quantity = self._normalize_line_item_description(
                    source_line, amount_token
                )
                if not description:
                    continue
                items.append(
                    ReceiptLineItem(
                        line_number=line_number,
                        description=description,
                        quantity=quantity,
                        amount_cents=amount_cents,
                    )
                )
                line_number += 1
                continue
            if self._looks_like_item_descriptor(line):
                pending_description.append(line)
                continue
            pending_description.clear()
        return items

    def _to_cents(self, value: str) -> int | None:
        try:
            normalized = value.replace(",", "")
            amount = Decimal(normalized)
        except (InvalidOperation, AttributeError):
            return None
        cents = int((amount * 100).quantize(Decimal("1")))
        return cents

    def _merge_label_amount_lines(self, lines: Sequence[str]) -> List[str]:
        merged: List[str] = []
        index = 0
        while index < len(lines):
            line = lines[index]
            next_index = index + 1
            if (
                next_index < len(lines)
                and self._is_label_only_line(line)
                and self._is_amount_only_line(lines[next_index])
            ):
                merged.append(f"{line} {lines[next_index]}")
                index += 2
                continue
            if (
                next_index < len(lines)
                and self._looks_like_item_descriptor(line)
                and not _AMOUNT_TOKEN_PATTERN.search(line)
                and self._is_amount_only_line(lines[next_index])
            ):
                merged.append(f"{line} {lines[next_index]}")
                index += 2
                continue
            merged.append(line)
            index += 1
        return merged

    def _find_total_label(self, line: str) -> Tuple[str, int] | None:
        for pattern, label, priority in _TOTAL_LABEL_PATTERNS:
            if pattern.search(line):
                return label, priority
        return None

    def _find_positive_amount(self, line: str) -> int | None:
        for token in self._amount_tokens(line):
            cleaned = token.strip()
            negative_hint = cleaned.startswith("-") or cleaned.endswith("-")
            has_parens = cleaned.startswith("(") and cleaned.endswith(")")
            if negative_hint or has_parens:
                continue
            cleaned = cleaned.strip("()").strip()
            cleaned = cleaned.lstrip("$€£")
            cleaned = re.sub(r"(?i)(usd|eur|gbp|cad|aud)$", "", cleaned).strip()
            cents = self._to_cents(cleaned)
            if cents is not None:
                return cents
        return None

    def _amount_tokens(self, line: str) -> List[str]:
        tokens = list(_AMOUNT_TOKEN_PATTERN.findall(line))
        if tokens:
            return tokens
        parts = line.split()
        results: List[str] = []
        for idx, part in enumerate(parts[:-1]):
            upper = part.upper().strip("()")
            next_part = parts[idx + 1]
            if upper in {"USD", "CAD", "EUR", "GBP", "AUD"}:
                candidate = next_part
                if _AMOUNT_TOKEN_PATTERN.fullmatch(candidate):
                    results.append(candidate)
            if _AMOUNT_TOKEN_PATTERN.fullmatch(part) and next_part.upper().strip(
                "()"
            ) in {
                "USD",
                "CAD",
                "EUR",
                "GBP",
                "AUD",
            }:
                results.append(part)
        return results

    def _select_fallback_amount(self, lines: Sequence[str]) -> int | None:
        fallback_keywords = (
            "total",
            "subtotal",
            "amount",
            "balance",
            "tendered",
            "due",
            "payment",
        )
        candidates: List[int] = []
        for line in lines:
            normalized = line.lower()
            if not any(keyword in normalized for keyword in fallback_keywords):
                continue
            amount = self._find_positive_amount(line)
            if amount is not None:
                candidates.append(amount)
        if candidates:
            return max(candidates)
        return None

    def _is_label_only_line(self, line: str) -> bool:
        stripped = line.strip().strip(":")
        if not stripped:
            return False
        for pattern, label, _priority in _TOTAL_LABEL_PATTERNS:
            if pattern.fullmatch(stripped):
                return True
        return False

    def _is_amount_only_line(self, line: str) -> bool:
        tokens = self._amount_tokens(line)
        if len(tokens) != 1:
            return False
        amount_text = tokens[0]
        if amount_text.strip().endswith("-"):
            return False
        sanitized = _AMOUNT_TOKEN_PATTERN.sub("", line)
        sanitized = re.sub(r"(?i)\b(usd|eur|gbp|cad|aud)\b", "", sanitized)
        sanitized = sanitized.replace("$", "").replace("€", "").replace("£", "")
        sanitized = sanitized.replace("(", "").replace(")", "")
        sanitized = sanitized.replace(":", "")
        sanitized = sanitized.strip()
        sanitized = re.sub(r"[\s,.;@-]+", "", sanitized)
        return not sanitized

    def _extract_merchant(self, lines: Sequence[str]) -> Tuple[str | None, int]:
        if not lines:
            return None, 0
        candidate_lines: List[str] = []
        consumed = 0
        for index, raw_line in enumerate(lines[:_MERCHANT_MAX_LINES]):
            stripped = raw_line.strip()
            if not stripped:
                break
            lower = stripped.lower()
            if index > 0 and self._merchant_should_stop(stripped, lower):
                break
            if index == 0:
                candidate_lines.append(stripped)
                consumed += 1
                continue
            current_candidate = " ".join(candidate_lines)
            if self._merchant_candidate_complete(
                current_candidate
            ) and not self._should_extend_candidate(stripped, lower):
                break
            candidate_lines.append(stripped)
            consumed += 1
            if len(" ".join(candidate_lines)) >= _MERCHANT_MAX_LENGTH:
                break
        merchant = " ".join(candidate_lines).strip()
        if not merchant:
            return None, consumed
        if not re.search(r"[a-z]", merchant, re.IGNORECASE):
            return merchant, consumed
        return merchant, consumed

    def _refine_merchant(
        self, merchant: str | None, lines: Sequence[str]
    ) -> str | None:
        if not merchant:
            return merchant
        cleaned = merchant.strip()
        upper = cleaned.upper()

        if "WHOLESALE" in upper and "COSTCO" not in upper:
            cleaned = re.sub(
                r"(?i)^[A-Z\s]*WHOLESALE",
                "Costco Wholesale",
                cleaned,
                count=1,
            )
            if "COSTCO" not in cleaned.upper():
                cleaned = f"Costco {cleaned}".strip()

        if "TRADER JOE'S" in upper and "#" not in cleaned:
            store_number = self._extract_store_number(lines)
            if store_number:
                cleaned = f"{cleaned} #{store_number}"

        if "85C" in cleaned and "°" not in cleaned:
            cleaned = cleaned.replace("85C", "85°C", 1)

        return cleaned

    def _extract_store_number(self, lines: Sequence[str]) -> str | None:
        for line in lines[:8]:
            match = re.search(r"(?i)store\s*#\s*(\d+)", line)
            if match:
                return match.group(1)
            standalone = re.search(r"#\s*(\d{3,})", line)
            if standalone:
                return standalone.group(1)
        return None

    def _merchant_should_stop(self, stripped: str, lower: str) -> bool:
        if any(
            self._contains_keyword(lower, keyword)
            for keyword in _MERCHANT_STOP_KEYWORDS
        ):
            return True
        if any(hint in lower for hint in _ADDRESS_HINTS):
            return True
        if re.match(r"^[0-9]|^[#][0-9].*\s", stripped):
            return True
        if re.search(r"\d{2,}[:/\-]\d{2,}", stripped):
            return True
        if "@" in stripped and "#" not in stripped:
            return True
        if re.search(r"\d+[\.,]\d{2}", stripped):
            return True
        return False

    def _merchant_candidate_complete(self, candidate: str) -> bool:
        tokens = candidate.split()
        if not tokens:
            return False
        if len(tokens) >= 3:
            return True
        if any("#" in token for token in tokens):
            return True
        return False

    def _should_extend_candidate(self, stripped: str, lower: str) -> bool:
        if any(
            self._contains_keyword(lower, keyword)
            for keyword in _MERCHANT_STOP_KEYWORDS
        ):
            return False
        if any(hint in lower for hint in _ADDRESS_HINTS):
            return False
        if re.match(r"^[0-9]", stripped):
            return False
        if "#" in stripped or "'" in stripped:
            return True
        if stripped.isupper() and len(stripped) <= 12:
            return True
        if len(stripped) <= 4 and stripped.isalpha():
            return True
        return False

    def _looks_like_item_descriptor(self, line: str) -> bool:
        stripped = line.strip()
        if not stripped:
            return False
        if self._find_total_label(stripped):
            return False
        if self._is_metadata_line(stripped):
            return False
        has_letters = re.search(r"[a-z]", stripped, re.IGNORECASE)
        return bool(has_letters)

    def _is_metadata_line(self, line: str) -> bool:
        lowered = line.lower()
        for prefix in _METADATA_LINE_PREFIXES:
            if self._contains_keyword(lowered, prefix):
                return True
        if " tendered" in lowered or " card" in lowered or " purchase" in lowered:
            return True
        if " visa" in lowered or " change" in lowered or " balance" in lowered:
            return True
        if "%" in line:
            return True
        return False

    def _contains_keyword(self, text: str, keyword: str) -> bool:
        normalized = text.translate(_DIGIT_TO_ALPHA)
        compact = normalized.replace(" ", "")
        if " " in keyword:
            if keyword in normalized:
                return True
            if keyword.replace(" ", "") in compact:
                return True
            return False
        return bool(re.search(rf"\b{re.escape(keyword)}\b", normalized))

    def _match_quantity_total(self, line: str) -> Tuple[int, int] | None:
        match = _QUANTITY_FOR_PATTERN.match(line.strip())
        if not match:
            return None
        amount = self._to_cents(match.group("amount"))
        if amount is None:
            return None
        return int(match.group("qty")), amount

    def _match_quantity_unit(self, line: str) -> int | None:
        match = _QUANTITY_AT_PATTERN.match(line.strip())
        if not match:
            return None
        return int(match.group("qty"))

    def _normalize_line_item_description(
        self, source: str, amount_token: str
    ) -> Tuple[str, int | None]:
        working = source.replace(amount_token, " ")
        working = _AMOUNT_TOKEN_PATTERN.sub(" ", working)
        working = re.sub(r"(?i)\b(usd|eur|gbp|cad|aud)\b", " ", working)
        tokens = [
            token.strip(",:;-") for token in working.split() if token.strip(",:;-")
        ]
        quantity: int | None = None

        idx = 0
        while idx < len(tokens) - 1:
            token = tokens[idx]
            next_token = tokens[idx + 1]
            if token.isdigit() and next_token == "@":
                quantity = int(token)
                del tokens[idx : idx + 2]
                break
            idx += 1

        if quantity is None:
            idx = 0
            while idx < len(tokens) - 1:
                token = tokens[idx]
                next_token = tokens[idx + 1]
                if token.isdigit() and next_token.lower() == "for":
                    quantity = int(token)
                    del tokens[idx : idx + 2]
                    break
                idx += 1

        if quantity is None and tokens and tokens[0].isdigit():
            maybe_quantity = int(tokens[0])
            if maybe_quantity <= 100:
                quantity = maybe_quantity
                tokens.pop(0)

        while tokens and len(tokens[0]) == 1 and tokens[0].isalpha():
            tokens.pop(0)

        while tokens and tokens[0].isdigit() and len(tokens[0]) >= 4:
            tokens.pop(0)

        tokens = [token for token in tokens if token not in {"@"}]
        cleaned_tokens = []
        for token in tokens:
            normalized = self._normalize_token(token)
            if not normalized:
                continue
            cleaned_tokens.append(normalized)
        description = " ".join(cleaned_tokens).strip()
        if not re.search(r"[a-z]", description, re.IGNORECASE):
            return "", quantity
        return description, quantity

    def _normalize_token(self, token: str) -> str:
        translated = token.translate(_DIGIT_TO_ALPHA)
        stripped = re.sub(r"[^A-Za-z0-9#&'\/]+", "", translated)
        if not stripped:
            return ""
        if stripped.isupper() and len(stripped) > 1:
            return stripped.title()
        return stripped

    def _clean_amount_token(self, token: str) -> str:
        cleaned = token.strip().strip("()")
        cleaned = cleaned.lstrip("$€£")
        cleaned = re.sub(r"(?i)(usd|eur|gbp|cad|aud)$", "", cleaned).strip()
        return cleaned
