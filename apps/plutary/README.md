# Plutary (Python)

*“Plutus, patron of wealth, favors the ledgers we keep.”*

Plutary now drives OCR through a Bazel-selected service binary: Apple Vision on macOS and PaddleOCR
elsewhere, all behind the same Python pipeline. The ingest CLI, preprocessing heuristics, and
heuristic parser remain Python-native; Bazel swaps the concrete OCR binary via the
`//apps/plutary:ocr_service_binary` alias so no runtime Bazel subprocesses are needed.

- `src/plutary/ocr/`: Preprocessing, binary adapter (`BinaryOcrService`), and Paddle-specific helpers.
- `src/plutary/cli/`: Command-line entrypoints for exercising the pipeline locally.
- `tests/`: PyTest suite, including golden JSON contracts for the Vision and Paddle binaries.
- `src/test/resources/examples/`: Sample receipt photos used by regression tests and manual checks.

## Current Capabilities
- `BinaryOcrService` packages the preprocessing pipeline and talks to whichever OCR binary Bazel
  selects (`Apple Vision` or `Paddle`) via a stdin/stdout JSON contract. The Python code no longer
  imports Paddle directly unless the Paddle backend is selected.
- `HybridReceiptImagePreprocessor` continues to reuse the OpenCV normalization pipeline before
  falling back to the Pillow defaults, preserving illumination correction, cropping, deskew, and
  contrast boosts that helped noisy cellphone captures.
- `receipt_cli` Bazel target runs the ingestion pipeline end-to-end (preprocessing, OCR service
  binary, heuristic parsing) with step-by-step trace output. When run without `--dry-run` it persists
  the ingestion, image, transaction summary, and line items to Postgres using the
  `DB_URL`/`DB_USER`/`DB_PASSWORD` settings (read from CLI args, environment, or `.env`). OpenCV
  preprocessing is skipped by default; pass `--use-advanced` to reinstate the OpenCV path.

The higher-level receipt ingestion flow (repositories, state machine, queue publishers) is being
ported next; those Java components remain in history for reference but are no longer built.

## Upcoming Extensibility
- **Composition module (planned):** A forthcoming `plutary.composition` package will replace the ad-hoc assembly in `receipt_cli.py` with factories such as `build_receipt_pipeline()` so CLIs, workers, and tests share the same wiring.
- **Provider hooks & registry:** Expect provider functions to override preprocessors, OCR services, parsers, and persistence adapters, plus a lightweight `register_processor("<merchant>")` helper for custom pipelines.
- **What to do today:** Continue injecting collaborators manually—swap in alternate `ReceiptImagePreprocessor`, `ReceiptOcrService`, or parser instances when constructing `ReceiptProcessingPipeline`, and wrap `ReceiptPersistenceService` if your processor needs different storage.
- **Stay aligned:** Track contribution guidance in `apps/plutary/AGENTS.md` and update both documents once the composition module is available.

## Native macOS Vision OCR (Service Binary)
- Requires macOS 12+ with Xcode command line tools so the Apple Vision framework is present.
- Builds with Bazelisk/Bazel 7.1.2 or newer (pinned via `.bazelversion`).
- Build the binary: `bazel build //apps/plutary/mac_vision_ocr:ocr_service`.
- Golden contract test (macOS hosts only): `bazel test //apps/plutary/mac_vision_ocr:ocr_service_golden_test`.
- Generate a sample JSON request and invoke the binary:
  ```bash
  PYTHONPATH=apps/plutary/src python3 - <<'PY' > /tmp/vision_request.json
  import base64, json
  from pathlib import Path

  from plutary.ocr import DefaultReceiptImagePreprocessor, ReceiptImage
  from plutary.ocr.reporting import noop_reporter

  image_path = Path("apps/plutary/src/test/resources/examples/sample_receipt_photo_01.jpeg")
  preprocessor = DefaultReceiptImagePreprocessor(reporter=noop_reporter)
  image_bytes = image_path.read_bytes()
  processed = preprocessor.preprocess(ReceiptImage(image_bytes, content_type="image/jpeg"))
  payload = {
      "image_png_base64": base64.b64encode(processed).decode("ascii"),
      "locale": "en_US",
      "minimum_confidence": 0.3,
      "recognition_level": "accurate",
  }
  print(json.dumps(payload))
  PY
  bazel run //apps/plutary/mac_vision_ocr:ocr_service < /tmp/vision_request.json
  ```
  - The service reads a single JSON object from stdin and writes a JSON response to stdout. See
    `apps/plutary/tests/ocr/golden/*.json` for the canonical responses.

## Development Workflow
1. Activate the repo-local virtualenv (`bazel run //:venv`) or another Python 3.12 environment.
2. Run the unit suite: `bazel test //apps/plutary:plutary_tests`.
   - The Bazel target now provisions an ephemeral PostgreSQL instance via Docker, applies
     the Flyway migrations under `ops/sql/plutary`, and points the tests at that database.
     Ensure Docker Desktop is running locally before launching the suite.
3. Dry-run the OCR CLI: `bazel run //apps/plutary:receipt_cli -- --image=apps/plutary/src/test/resources/examples/sample_receipt_photo_01.jpeg --dry-run`.
   - Use `--locale`, `--minimum-confidence`, or `--recognition-level` to adjust the request sent to
     the backend. `--ocr-backend=paddle|vision|auto` controls only the expected backend log output;
     Bazel selects the actual binary by wiring the `//apps/plutary:ocr_service_binary` alias into the
     CLI's runfiles (use `--define=ocr_backend=paddle` when invoking Bazel to force Paddle).
   - Other curated fixtures live under `src/test/resources/examples/`; try
     `sample_receipt_photo_05.jpeg` for the Costco multi-quantity receipt.
4. Update this README and `AGENTS.md` as new components come online.

## Testing
```
bazel test //apps/plutary:plutary_tests
```
- Spins up a disposable PostgreSQL container, applies the Plutary Flyway migrations, and runs the
  PyTest suite against that schema. Each test runs inside its own transaction and rolls back on
  completion, so the database stays pristine.
- Requires Docker (or another engine compatible with the `docker` CLI) on the host.
- CLI persistence coverage lives in `apps/plutary/tests/persistence/test_receipt_cli_persistence.py`.
  The tests invoke the real CLI end-to-end using stubbed OCR pipelines and assert database writes
  via the reusable `RecordFactory` helper (`apps/plutary/tests/util/record_factory.py`).

## CLI Preview
```
bazel run //apps/plutary:receipt_cli -- --image=apps/plutary/src/test/resources/examples/sample_receipt_photo_01.jpeg --dry-run
```
- Emits a stage-by-stage trace (preprocessing, OCR backend, parsing) and prints the JSON payload that
  would be stored. Configure the backend request with `--locale`, `--minimum-confidence`, and
  `--recognition-level`. Use `--use-advanced` to enable the OpenCV preprocessing pipeline.
- Omit `--dry-run` to persist the result; supply database credentials via CLI arguments
  (`--db-url`, `--db-user`, `--db-password`) or ensure the environment/.env file provides them.

## Receipt Parser Heuristics
- The heuristic parser now reconstructs the merchant heading by joining the first few OCR lines
  until it encounters receipt metadata (e.g., subtotal, tax, tendered). This captures multi-line
  brands such as `TRADER` + `JOE'S` + `#0162` without pulling the street address into the name.
- Line-item detection merges description-only lines followed by amount-only lines before applying
  regex extraction. Leading flags (e.g., `E`, `T`) and SKU tokens are stripped, and quantity hints
  like `1 Croissant`, `4 for 11.99`, or `4 @ $3.00` populate the `quantity` field when obvious.
- Metadata rows (`Visa Approved`, `Amount 189.86`, etc.) are filtered so totals and payment
  summaries no longer contaminate the line-item list.

### Refreshing OCR Snapshots
1. Run the CLI with `--dry-run` against the target receipt image (e.g.,
   `bazel run //apps/plutary:receipt_cli -- --image=... --dry-run`).
2. Copy the emitted `ocr_text` block from the JSON payload into the regression tests under
   `apps/plutary/tests/receipts/` (or create a new fixture alongside the existing samples).
3. Update the ground-truth fixture in `src/test/resources/ground_truth/` if the canonical metadata
   changes, and extend the PyTest coverage so the new OCR text exercises the parser heuristics.
4. If the JSON contract changes, refresh the golden request/response pairs in
   `apps/plutary/tests/ocr/golden/` and update the corresponding tests for both backends.

## Next Up
- Port repositories, ingestion state machine, and queue publishers from Java to Python modules.
- Expose a gRPC/HTTP surface for native clients once the Python backend reaches feature parity.
- Capture backend performance metrics (Vision vs Paddle) and surface them in the CLI/logging.
