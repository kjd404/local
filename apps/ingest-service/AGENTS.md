# Ingest Service Guidelines

Refer to the shared [apps/AGENTS.md](../AGENTS.md) for overarching application conventions.

In this service:

- Use constructor-based dependency injection.
- Keep classes small and cohesive.
- Prefer integration tests with a temporary database.

## Bazel Targets

- Build binary: `bazel build //apps/ingest-service:ingest_app`
- Run binary: `bazel run //apps/ingest-service:ingest_app -- --mode=scan`
- New-account CLI: `bazel run //apps/ingest-service:new_account_cli`

Notes
- Dependencies are resolved via bzlmod (`MODULE.bazel` + `rules_jvm_external`).
- Resources under `src/main/resources/mappings` are packaged and available on the classpath (e.g., `/mappings/example.yaml`).
- Tests run under Bazel via JUnit 5 ConsoleLauncher: `bazel test //apps/ingest-service:ingest_tests`.
