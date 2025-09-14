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

## Commands & Permissions

- Slash commands:
  - `/challenge @user <topic> [private]` – Starts a debate; `private=true` creates a hidden channel.
  - `/debate_end` – Ends the debate and locks the channel read-only.
  - `/sync` – Force-resync commands (requires Manage Server).
- Intents: Enable "Message Content Intent" in the Developer Portal.
- Role perms: Ensure the bot role has "Manage Channels" and "Manage Permissions" and can "Send Messages". The bot adds a channel overwrite for itself when creating debate channels.

## Behavior

- Turn-taking: Challenger starts. After each participant message, the bot flips who can send and posts "Your turn, @user".
- Persistence: Debate metadata is stored in the channel topic. On startup, the bot restores debates and infers whose turn it is from channel permissions.

## Troubleshooting

- Privileged intents error: Enable Message Content Intent.
- Commands not visible: Ensure the bot was invited with `applications.commands` scope; use `/sync`. With `DISCORD_GUILD_ID` set, commands are guild-scoped and appear instantly.
- Missing permissions on Accept: Make sure the bot role is above participants and has "Manage Channels" and "Send Messages".
