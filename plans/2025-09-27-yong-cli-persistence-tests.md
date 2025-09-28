# Plan: Yong CLI Persistence Test Coverage

## Overview / Goal
- Add Bazel-backed pytest coverage that exercises the Yong receipt CLI end-to-end through persistence, validating inserts and updates against the ephemeral PostgreSQL instance.
- Provide reusable test utilities (record factory, CLI runner hook) so future scenarios can seed and interrogate receipt data without duplicating SQL snippets.

## Current State
- The CLI currently drives OCR, parsing, and persistence before returning success (apps/yong/src/yong/cli/receipt_cli.py:134).
- Persistence writes ingestion metadata, image payload, transaction summary, and line items with conflict handling (apps/yong/src/yong/persistence/receipt_repository.py:25).
- Tests configure a shared psycopg connection scoped per test via autouse fixture, ensuring every test sees a clean transaction (apps/yong/tests/conftest.py:16).
- The existing persistence test module only validates configuration helpers; there is no end-to-end verification of database writes (apps/yong/tests/persistence/test_config.py:1).

## Proposed Changes
1. **Introduce a pipeline factory seam in the CLI**
   - Factor the pipeline construction (Paddle OCR + parser + pipeline) into a `_create_pipeline` helper so tests can monkeypatch it with deterministic stubs without touching production flow (apps/yong/src/yong/cli/receipt_cli.py:134).
   - Allow the helper to accept a reporter and args; return the pipeline instance used by `main`. Default behavior remains unchanged for runtime usage.

2. **Add a record factory utility for database state management**
   - Create `apps/yong/tests/util/record_factory.py` with a `RecordFactory` class that accepts the shared psycopg connection and exposes `create_record(model_cls, overrides=None)` plus convenient fetch helpers.
   - Support at least `ReceiptIngestionRecord`, `ReceiptTransaction`, and line-item creation by mapping defaults to the underlying schema, using explicit SQL to persist data. Return simple dataclass/Dict representations for assertions.
   - Provide defaults that align with the current schema (ops/sql/yong/V1__create_receipt_tables.sql:1, ops/sql/yong/V3__add_transaction_tables.sql:1) while allowing overrides per call.

3. **Extend pytest fixtures for CLI tests**
   - Add a fixture `record_factory` that yields the shared `RecordFactory` bound to `shared_connection` (apps/yong/tests/conftest.py:16).
   - Add a fixture `cli_runner` that invokes `receipt_cli.main(argv)` while capturing stdout/stderr and passing explicit DB credentials from `DatabaseSettings`, easing repeated CLI invocation in tests.

4. **Author CLI persistence integration tests**
   - New module `apps/yong/tests/persistence/test_receipt_cli_persistence.py` covering:
     - `test_cli_persists_new_receipt`: monkeypatch `_create_pipeline` to return a stub pipeline producing a fixed `ReceiptProcessingResult` with deterministic IDs, run CLI against a dummy image, then assert rows exist in `receipt_ingestions`, `receipt_images`, `receipt_transactions`, and `receipt_line_items` using SQL helpers.
     - `test_cli_replaces_existing_transaction`: seed baseline ingestion/transaction/line items via `RecordFactory`, configure stub pipeline to emit the same ingestion ID with altered totals/items, run CLI again, and assert merchant/amount updates and prior line items are replaced.
   - Ensure stubs verify the CLI still prints summaries and that persistence uses the image bytes size/checksum in the database.

5. **Document usage in AGENTS or README if needed**
   - Add a short note to `apps/yong/README.md` testing section about the new CLI persistence tests and record factory helper to guide contributors.

## Implementation Status
- [x] CLI `_create_pipeline` helper allows deterministic stubbing in tests.
- [x] RecordFactory utility plus `record_factory`/`cli_runner` fixtures wired into pytest.
- [x] End-to-end CLI persistence tests cover initial insert and update flows.
- [x] README testing docs call out the new coverage and helpers.
- [x] Flyway test harness copies migration SQL into a Docker-friendly directory before running migrations.

## Testing & Verification
- Automated: `bazel test //apps/yong:yong_tests`
- Manual (optional during development): run the new test module directly via `bazel test //apps/yong/tests/persistence:test_receipt_cli_persistence` once a dedicated target exists, or `pytest apps/yong/tests/persistence/test_receipt_cli_persistence.py` inside `bazel run //:venv`.

## Success Criteria

### Automated Verification
- [x] `bazel test //apps/yong:yong_tests` passes locally.
- [x] New tests confirm inserted rows match the stubbed pipeline output (merchant, totals, currency, occurred_at).
- [x] Update scenario proves line items are replaced and transaction totals updated on re-run.

### Manual Verification
- [ ] (Optional) Inspect Postgres tables after running the CLI locally to see persisted data for a sample receipt.

## Risks & Mitigations
- **Heavy dependencies**: Paddle OCR imports may still load during tests; stubbing via `_create_pipeline` avoids instantiating real engines.
- **Flaky DB teardown**: Ensure record factory and CLI tests commit/rollback within the `transactional_db` fixture to keep the ephemeral database clean.
- **Checksum assertions**: Byte-for-byte checks require deterministic image bytes; use generated dummy files in tests to remove variability.

## Out of Scope
- Migrating the worker/service orchestration to Python or wiring persistence outside the CLI.
- Validating actual OCR output or parser heuristics (those remain in existing tests).
- Adding new Bazel targets beyond the updated `yong_tests` aggregation.

## Follow-ups / Future Enhancements
- Expand record factory coverage to other tables as additional persistence features migrate.
- Reuse the CLI runner fixture for forthcoming service-level tests once the orchestrator is ported to Python.
