# Plutary Service Guidelines (Python Migration)

Follow the shared conventions in [`apps/AGENTS.md`](../AGENTS.md): constructor injection,
small cohesive classes, and Bazel-first builds for backend components.

## Operating Mode
- Work test-first with PyTest. Add or update a failing test before production changes, then make
  it pass and refactor.
- Keep collaborators injectable; continue wiring dependencies through the current `plutary` modules so
  higher-level tests can swap fakes for Paddle, the database, or queue publishers. The upcoming
  `plutary.composition` package will centralize these factories (see **Extensibility & Composition**);
  until it ships, add TODO markers rather than importing a placeholder module.
- Update this document and `README.md` whenever new Python features land or Java parity changes.

## Testing Infrastructure
- `bazel test //apps/plutary:plutary_tests` drives the PyTest suite covering preprocessing, the binary
  OCR adapter, and receipt parsing heuristics. The suite now includes golden JSON contract tests
  that spawn the Paddle service binary when the backend is available.
  - The Paddle binary exposes `PaddleOcrApplication` and `build_default_app()` so unit tests can
    compose fakes without monkeypatching or touching module globals; prefer injecting stubs through
    those constructors when exercising failure modes.
- `bazel test //apps/plutary/tests/ocr:test_paddle_service_bin` offers fast, hermetic validation of the
  Paddle binary: it feeds the same JSON golden fixtures through a subprocess and also exercises the
  DI seams with in-memory fakes to assert exit codes 2, 3, and 4.
- Sample receipt photos remain under `src/test/resources/examples/`; reuse them for future
  contract or integration tests. Golden responses live under `tests/ocr/golden/` and the JSON
  requests are generated on the fly from those shared samples so we avoid committing large base64
  payloads.
- On macOS hosts, `bazel test //apps/plutary/mac_vision_ocr:ocr_service_golden_test` validates the
  Vision binary against the shared JSON fixtures. The target skips automatically on non-Darwin
  platforms to keep CI stable.

## Extensibility & Composition
- **Pipeline assembly today:** `plutary/cli/receipt_cli.py` builds the `ReceiptProcessingPipeline`, selects preprocessors with `--use-advanced`, wires the `BinaryOcrService`, and pairs it with `HeuristicReceiptParser`; custom flows still hand-wire those collaborators.
- **OCR adapters:** `BinaryOcrService` accepts any `ReceiptImagePreprocessor`, overrideable runfile location, env map, timeout, and process runner, so teams can swap OCR backends without touching pipeline code.
- **Persistence seam:** `DatabaseSettings`, `Database`, and `ReceiptPersistenceService` isolate storage concerns; alternative processors can inject their own persistence adapter or persist off the `ReceiptProcessingResult` payload.
- **Planned `plutary.composition` surface:** The module will own `build_receipt_pipeline()` and companion factories, expose provider hooks for preprocessors/OCR/parsers/persistence, and carry a registry (`register_processor`) for merchant-specific pipelines while keeping today’s defaults.
- **Contributor guidance:** Track seam changes here, reuse wording from the `README` “Upcoming Extensibility” section, and flip language from “upcoming” to concrete APIs once the module lands.

## Progress & Next Steps
- **Current Status**: The OCR pipeline now calls a Bazel-selected service binary (`ocr_service_binary`)
  so macOS hosts default to Apple Vision and other platforms fall back to Paddle without runtime
  Bazel subprocesses. The Python CLI continues to orchestrate preprocessing, OCR, parsing, and
  optional persistence.
- **Immediate Tasks**: Translate repositories, state machine, and CLI persistence paths to Python;
  capture backend selection/latency metrics, and keep the JSON contract fixtures current.
- **Upcoming Milestones**: Add an OCR microservice sidecar, document deployment ergonomics, and
  extend contract tests as more receipt scenarios are curated.
