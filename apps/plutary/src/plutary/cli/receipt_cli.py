"""Command-line utility for running Plutary OCR via the Bazel-selected backend."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from plutary.persistence import (
    Database,
    DatabaseConfigError,
    DatabaseSettings,
    ReceiptPersistenceService,
)

try:  # Optional diagnostic import to surface missing extras early.
    from paddlex.utils import deps as paddlex_deps  # type: ignore[import]
except (
    Exception
):  # pragma: no cover - paddlex may not be present until runtime deps resolve.
    paddlex_deps = None

from plutary.ocr import (
    BinaryOcrService,
    DefaultReceiptImagePreprocessor,
    HybridReceiptImagePreprocessor,
    OpenCvReceiptImageProcessor,
)
from plutary.receipts import (
    HeuristicReceiptParser,
    ReceiptProcessingContext,
    ReceiptProcessingPipeline,
    ReceiptProcessingResult,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run Plutary's OCR pipeline via the platform-selected backend"
    )
    parser.add_argument(
        "--image", required=True, help="Path to the receipt image to parse"
    )
    parser.add_argument(
        "--locale",
        "--lang",
        dest="locale",
        default="en_US",
        help="Locale passed to the OCR backend (default: en_US)",
    )
    parser.add_argument(
        "--minimum-confidence",
        type=float,
        default=0.3,
        help="Confidence threshold forwarded to the backend (default: 0.3)",
    )
    parser.add_argument(
        "--recognition-level",
        choices=("accurate", "fast"),
        default="accurate",
        help="Recognition profile requested from the backend (default: accurate)",
    )
    parser.add_argument(
        "--ocr-backend",
        choices=("auto", "vision", "paddle"),
        default="auto",
        help="Expected OCR backend (auto = Bazel-selected). Use --define=ocr_backend=paddle to force Paddle",
    )
    parser.add_argument(
        "--skip-advanced",
        action="store_true",
        default=True,
        help="Skip OpenCV preprocessing and rely solely on the default image pipeline (default)",
    )
    parser.add_argument(
        "--use-advanced",
        dest="skip_advanced",
        action="store_false",
        help="Enable OpenCV preprocessing prior to PaddleOCR",
    )
    parser.add_argument(
        "--account",
        default="cli-account",
        help="Account external ID used when constructing the ingestion record",
    )
    parser.add_argument(
        "--client-receipt-id",
        help="Client-supplied receipt identifier (defaults to a generated UUID)",
    )
    parser.add_argument(
        "--captured-at",
        help="ISO-8601 timestamp for when the receipt was captured (defaults to now)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run without persistence and print the object that would be stored",
    )
    parser.add_argument(
        "--db-url",
        help="PostgreSQL connection URL (overrides DB_URL / DATABASE_URL)",
    )
    parser.add_argument(
        "--db-user",
        help="Database user (overrides DB_USER / DATABASE_USER)",
    )
    parser.add_argument(
        "--db-password",
        help="Database password (overrides DB_PASSWORD / DATABASE_PASSWORD)",
    )
    parser.add_argument(
        "--env-file",
        default=".env",
        help="Optional env file to load DB defaults from (default: .env)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    image_path = Path(args.image)
    if not image_path.is_file():
        parser.error(f"Receipt image not found: {image_path}")

    if not 0.0 <= args.minimum_confidence <= 1.0:
        parser.error("--minimum-confidence must be between 0.0 and 1.0")

    image_bytes = image_path.read_bytes()

    reporter = _make_reporter()
    pipeline = _create_pipeline(args=args, reporter=reporter)

    context = ReceiptProcessingContext(
        account_external_id=args.account,
        client_receipt_id=args.client_receipt_id or _generate_uuid(),
        captured_at=_parse_captured_at(args.captured_at),
        content_type=_guess_content_type(image_path),
    )

    result = pipeline.process(image_bytes, context)
    _print_summary(result)

    if args.dry_run:
        print("(dry-run) receipt not persisted to database")
        return 0

    try:
        settings = DatabaseSettings.from_sources(
            explicit={
                "DB_URL": args.db_url,
                "DB_USER": args.db_user,
                "DB_PASSWORD": args.db_password,
            },
            env_file=args.env_file,
        )
    except DatabaseConfigError as exc:
        parser.error(str(exc))

    persistence = ReceiptPersistenceService(Database(settings))
    persistence.persist(result=result, context=context, image_bytes=image_bytes)
    print("(info) receipt persisted to database")
    return 0


def _build_preprocessor(
    *, skip_advanced: bool, reporter
) -> DefaultReceiptImagePreprocessor | HybridReceiptImagePreprocessor:
    fallback = DefaultReceiptImagePreprocessor(reporter=reporter)
    if skip_advanced:
        return fallback
    return HybridReceiptImagePreprocessor(
        OpenCvReceiptImageProcessor(reporter=reporter),
        fallback,
        reporter=reporter,
    )


def _create_pipeline(
    *, args: argparse.Namespace, reporter
) -> ReceiptProcessingPipeline:
    preprocessor = _build_preprocessor(
        skip_advanced=args.skip_advanced, reporter=reporter
    )
    service = BinaryOcrService(
        preprocessor,
        locale=_normalize_locale(args.locale),
        minimum_confidence=args.minimum_confidence,
        recognition_level=args.recognition_level,
        step_reporter=reporter,
    )

    _report_backend_selection(
        service=service,
        expected=args.ocr_backend,
        reporter=reporter,
    )
    if service.backend_name == "paddle":
        _warn_missing_paddle_deps(reporter)

    parser_service = HeuristicReceiptParser()
    return ReceiptProcessingPipeline(service, parser_service, step_reporter=reporter)


def _normalize_locale(value: str) -> str:
    if not value:
        return "en_US"
    normalized = value.replace("-", "_")
    parts = normalized.split("_", 1)
    if len(parts) == 2 and parts[0] and parts[1]:
        return f"{parts[0].lower()}_{parts[1].upper()}"
    if len(parts[0]) == 2:
        return f"{parts[0].lower()}_{parts[0].upper()}"
    return normalized


def _report_backend_selection(
    *, service: BinaryOcrService, expected: str, reporter
) -> None:
    backend = service.backend_name
    reporter("ocr", f"Selected OCR backend: {backend}")
    if expected != "auto" and backend != expected:
        reporter(
            "ocr",
            "Requested backend '%s' but Bazel resolved to '%s'. "
            "Use --define=ocr_backend=%s when invoking bazel run to force the target."
            % (expected, backend, expected),
        )


def _warn_missing_paddle_deps(reporter) -> None:
    if paddlex_deps is None:
        return
    if paddlex_deps.is_extra_available("ocr"):
        return
    missing = [
        dep
        for dep, flags in paddlex_deps.EXTRAS.get("ocr", {}).items()
        if not paddlex_deps.is_dep_available(dep)
    ]
    reporter(
        "deps",
        "PaddleX OCR extras missing runtime dependencies: "
        + (", ".join(missing) if missing else "(unknown)"),
    )


def _guess_content_type(path: Path) -> str | None:
    suffix = path.suffix.lower()
    if suffix in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if suffix == ".png":
        return "image/png"
    return None


def _make_reporter():
    def report(stage: str, message: str) -> None:
        print(f"[{stage}] {message}")

    return report


def _generate_uuid() -> str:
    import uuid

    return str(uuid.uuid4())


def _parse_captured_at(value: str | None) -> datetime:
    if not value:
        return datetime.now(timezone.utc)
    try:
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            return parsed.replace(tzinfo=timezone.utc)
        return parsed
    except ValueError:
        raise SystemExit(f"Invalid --captured-at timestamp: {value}") from None


def _print_summary(result: ReceiptProcessingResult) -> None:
    record = result.record
    parsed = result.parsed_receipt

    print("--- Plutary Receipt Processing ---")
    print(f"Receipt ID           : {record.id}")
    print(f"Account External ID  : {record.account_external_id}")
    print(f"Client Receipt ID    : {record.client_receipt_id}")
    print(f"Captured At          : {record.captured_at.isoformat()}")
    print(f"Status               : {record.status.value}")
    if record.failure_reason:
        print(f"Failure Reason       : {record.failure_reason}")

    preview = _summarize_text(record.ocr_text)
    if preview:
        print("OCR Text (preview)   :")
        for line in preview.splitlines():
            print(f"  {line}")

    transaction = parsed.transaction
    if transaction:
        print("Transaction (preview):", transaction)
    if parsed.line_items:
        print("Line Items (preview) :")
        for item in parsed.line_items:
            print(f"  {item}")

    payload = result.to_dict()
    print("Insert Payload       :")
    print(json.dumps(payload, indent=2))


def _summarize_text(text: str, *, max_lines: int = 8) -> str:
    lines = [line.rstrip() for line in text.splitlines() if line.strip()]
    if len(lines) <= max_lines:
        return "\n".join(lines)
    truncated = lines[: max_lines - 1]
    truncated.append("…")
    return "\n".join(truncated)


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
