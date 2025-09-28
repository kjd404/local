# Yong (Python)

Yong is migrating to a Python-first implementation so the backend can adopt PaddleOCR
without shelling out to Tesseract. The service continues to orchestrate receipt ingestion,
OCR, and LLM-assisted parsing, with the OCR pipeline now written in Python.

- `src/yong/ocr/`: Paddle-backed OCR adapters and preprocessing pipeline.
- `src/yong/cli/`: Command-line entrypoints for exercising the pipeline locally.
- `tests/`: PyTest suite (mirrors the former JUnit coverage during the migration).
- `src/test/resources/examples/`: Sample receipt photos used by regression tests and manual checks.

## Current Capabilities
- `PaddleReceiptOcrService` mirrors the legacy rotation and scoring heuristics while delegating
  to a resident `PaddleOCR` engine (angle classifier enabled by default).
- `HybridReceiptImagePreprocessor` reuses the OpenCV normalization pipeline before falling back
  to the Pillow-based defaults, preserving illumination correction, cropping, deskew, and contrast
  boosts that helped noisy cellphone captures.
- `receipt_cli` Bazel target now runs the full ingestion pipeline end-to-end (preprocessing,
  Paddle OCR, heuristic parsing) with step-by-step trace output. When run without `--dry-run` it
  persists the ingestion, image, transaction summary, and line items to Postgres using the
  `DB_URL`/`DB_USER`/`DB_PASSWORD` settings (read from CLI args, environment, or `.env`). OpenCV
  preprocessing is skipped by default because Paddle's models handle denoising; pass `--use-advanced`
  if you want to reinstate the OpenCV pipeline. Additional flags expose PaddleOCR tuning knobs such
  as `--ocr-version`, `--use-doc-unwarping`, and `--use-textline-orientation`.

The higher-level receipt ingestion flow (repositories, state machine, queue publishers) is being
ported next; those Java components remain in history for reference but are no longer built.

## Development Workflow
1. Activate the repo-local virtualenv (`bazel run //:venv`) or another Python 3.12 environment.
2. Run the unit suite: `bazel test //apps/yong:yong_tests`.
   - The Bazel target now provisions an ephemeral PostgreSQL instance via Docker, applies
     the Flyway migrations under `ops/sql/yong`, and points the tests at that database.
     Ensure Docker Desktop is running locally before launching the suite.
3. Dry-run the OCR CLI: `bazel run //apps/yong:receipt_cli -- --image=apps/yong/src/test/resources/examples/sample_receipt_photo_01.jpeg --dry-run`.
4. Update this README and `AGENTS.md` as new Python components come online.

## Testing
```
bazel test //apps/yong:yong_tests
```
- Spins up a disposable PostgreSQL container, applies the Yong Flyway migrations, and runs the
  PyTest suite against that schema. Each test runs inside its own transaction and rolls back on
  completion, so the database stays pristine.
- Requires Docker (or another engine compatible with the `docker` CLI) on the host.
- CLI persistence coverage lives in `apps/yong/tests/persistence/test_receipt_cli_persistence.py`.
  The tests invoke the real CLI end-to-end using stubbed OCR pipelines and assert database writes
  via the reusable `RecordFactory` helper (`apps/yong/tests/util/record_factory.py`).

## CLI Preview
```
bazel run //apps/yong:receipt_cli -- --image=apps/yong/src/test/resources/examples/sample_receipt_photo_01.jpeg --dry-run
```
- Emits a stage-by-stage trace (preprocessing, rotations, parsing) and prints the JSON payload that
  would be stored. `--rotation 0 90` customizes evaluated angles; `--lang` selects Paddle's language
  pack (defaults to `en`); use `--use-advanced` if you want to opt back into the OpenCV pipeline.
- Toggle Paddle options with `--use-doc-unwarping`, `--use-doc-orientation-classify`,
  `--use-textline-orientation`, or `--ocr-version=PP-OCRv5` when experimenting.
- Omit `--dry-run` to persist the result; supply database credentials via CLI arguments (
  `--db-url`, `--db-user`, `--db-password`) or ensure the environment/.env file provides them.

## Receipt Parser Heuristics
- The heuristic parser now reconstructs the merchant heading by joining the first few OCR lines
  until it encounters receipt metadata (e.g., subtotal, tax, tendered). This captures multi-line
  brands such as `TRADER` + `JOE'S` + `#0162` without pulling the street address into the name.
- Line-item detection merges description-only lines followed by amount-only lines before applying
  regex extraction. Leading flags (e.g., `E`, `T`) and SKU tokens are stripped, and quantity hints
  like `1 Croissant`, `4 for 11.99`, or `4 @ $3.00` populate the `quantity` field when obvious.
- Metadata rows (`Visa Approved`, `Amount 189.86`, etc.) are filtered so totals and payment
  summaries no longer contaminate the line-item list.

### Refreshing Paddle OCR Snapshots
1. Run the CLI with `--dry-run` against the target receipt image (e.g.,
   `bazel run //apps/yong:receipt_cli -- --image=... --dry-run`).
2. Copy the emitted `ocr_text` block from the JSON payload into the regression tests under
   `apps/yong/tests/receipts/` (or create a new fixture alongside the existing samples).
3. Update the ground-truth fixture in `src/test/resources/ground_truth/` if the canonical metadata
   changes, and extend the PyTest coverage so the new OCR text exercises the parser heuristics.

## Next Up
- Port repositories, ingestion state machine, and queue publishers from Java to Python modules.
- Expose a gRPC/HTTP surface for native clients once the Python backend reaches feature parity.
- Add contract tests that compare Paddle output against ground-truth fixtures to guard future
  upgrades of Paddle or preprocessing heuristics.
