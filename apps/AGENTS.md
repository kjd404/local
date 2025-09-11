# Apps Guidelines

## Object-Oriented Design

Prefer dependency injection and object composition as described in
*Dependency Injection: Principles, Practices, and Patterns* by Mark Seemann and Steven van Deursen.
Follow the guidelines in Yegor Bugayenko's *Elegant Objects* for small, cohesive classes
and constructor-based immutability.

## Testing

- Development is test-driven; keep tests colocated with the code they cover.
- Use Bazel for all builds and tests: `bazel test //...`.
- Favor integration tests that exercise end-to-end flows; tag heavyweight tests (`integration`, `e2e`) and exclude by default in CI.

## Build & Tooling

- Bazel is the single entry point. Prefer `bazel run` wrappers over ad-hoc scripts.
- Python tooling is first-class:
  - Create local venv: `bazel run //:venv`
  - Update lockfile: `bazel run //:lock` (from `requirements.in`)
  - Bazel and the venv both use `requirements.lock` to avoid drift.

## Database Migrations

- Service-owned migrations live under `ops/sql/<service>`.
- Define a runnable with the shared macro `flyway_migration` from `//tools/sql:flyway.bzl`.
- Provide DB vars via `.env` (git-ignored) using a service prefix, e.g. `SERVICEA_DB_URL`.
