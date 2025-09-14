# Athena Bot Guidelines

Refer to the shared apps/AGENTS.md for overarching application conventions.

## Design

- Test-first (TDD): write a failing test, then implement the smallest change to pass.
- Object-Oriented Design: prefer dependency injection and composition (Mark Seemann, Elegant Objects). Keep classes small, cohesive, and constructor-initialized.
- Side-effect isolation: separate configuration, wiring, and runtime concerns. Avoid real network calls in tests.

## Bazel Targets

- Run bot: `bazel run //apps/athena-bot:bot`
- Run tests: `bazel test //apps/athena-bot:bot_tests`
- Docker image helpers:
  - Build: `bazel run //apps/athena-bot/docker:build_image -- --tag=athena-bot:latest`
  - Run: `bazel run //apps/athena-bot/docker:run_container -- --image=athena-bot:latest`
  - Logs: `bazel run //apps/athena-bot/docker:logs`

## Environment

- `DISCORD_BOT_TOKEN` is required to start the bot. Provide it in your shell or via the repo-local `.env` (git-ignored). The Docker run wrapper will load `.env` automatically.
