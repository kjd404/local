# Yong Service Guidelines (Python Migration)

Follow the shared conventions in [`apps/AGENTS.md`](../AGENTS.md): constructor injection,
small cohesive classes, and Bazel-first builds for backend components.

## Operating Mode
- Work test-first with PyTest. Add or update a failing test before production changes, then make
  it pass and refactor.
- Keep collaborators injectable (factories live in `yong.composition` when added) so higher-level
  tests can swap fakes for Paddle, the database, or queue publishers.
- Update this document and `README.md` whenever new Python features land or Java parity changes.

## Testing Infrastructure
- `bazel test //apps/yong:yong_tests` drives the PyTest suite covering OCR, preprocessing, and the
  receipt pipeline heuristics.
- Sample receipt photos remain under `src/test/resources/examples/`; reuse them for future
  contract or integration tests. Plan to add slow tests guarded by an environment flag when
  hitting the real Paddle models.

## Progress & Next Steps
- **Current Status**: Paddle-based OCR pipeline (preprocessing + adapter) feeds a Python CLI that
  exercises the ingestion pipeline end-to-end with step tracing and dry-run previews. Higher-level
  ingestion components (repositories/state machine) are being ported from Java next.
- **Immediate Tasks**: Translate repositories, state machine, and CLI persistence paths to Python;
  reintroduce database harnesses and gRPC façade once the core domain is available.
- **Upcoming Milestones**: Add an OCR microservice sidecar, document deployment ergonomics, and
  layer contract tests that compare Paddle outputs to the historical ground-truth fixtures.
