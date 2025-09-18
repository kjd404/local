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
- `ATHENA_PERSONAS_FILE` (optional) points to a YAML file defining personas. Default: `athena_bot/resources/personas.yaml`.

## Commands & Permissions

- Slash commands:
  - `/challenge @user <topic> [private]` – Starts a debate; `private=true` creates a hidden channel.
  - `/debate_end` – Ends the debate and locks the channel read-only.
  - `/sync` – Force-resync commands (requires Manage Server).
  - `/bots_debate <topic> [private] [rounds] [left] [right] [temperature] [concise]` – Starts an automated LLM-vs-LLM debate (defaults: Alex vs Riley). `concise=true` clamps temperature and length. Requires Manage Webhooks.
  - `/bots_personas` – Lists available personas and summaries.
- Intents: Enable "Message Content Intent" in the Developer Portal.
- Role perms: Ensure the bot role has "Manage Channels" and "Manage Permissions" and can "Send Messages". The bot adds a channel overwrite for itself when creating debate channels.
  - For `/bots_debate`, also grant "Manage Webhooks" to allow persona-styled messages. If unavailable, messages fall back to standard bot posts with a name prefix.

## Behavior

- Turn-taking: Challenger starts. After each participant message, the bot flips who can send and posts "Your turn, @user".
- Persistence: Debate metadata is stored in the channel topic. On startup, the bot restores debates and infers whose turn it is from channel permissions.
  - Agent debates use a separate topic prefix and do not require turn-flip permissions; the bot orchestrates turns internally. Openings include a single self‑intro; subsequent turns avoid greetings and identity restatements.

## Current Status & Goals

- Scope: Add automated LLM‑vs‑LLM debates alongside human debates.
- Done:
  - New commands `/bots_debate` and `/bots_personas` registered in `athena_bot/bot.py`.
  - Orchestrator `athena_bot/agent_debate.py` posts alternating persona messages via webhook; falls back to normal posts if webhooks unavailable.
  - Pluggable LLM interface in `athena_bot/llm/` with default `OpenAIChatModel` (lazy import; requires `OPENAI_API_KEY`).
  - Personas loader in `athena_bot/personas.py` with built‑ins and YAML override (`ATHENA_PERSONAS_FILE`); default YAML at `athena_bot/resources/personas.yaml`.
  - Tests: `tests/athena_bot/test_agent_debate.py` covers turn alternation and webhook usage.
  - Bazel: `BUILD.bazel` includes personas YAML as `data` and adds `openai` + `pyyaml` deps.
  - Deps/env: `requirements.in`/`requirements.lock` updated; `.env-sample` documents `OPENAI_*` and `ATHENA_PERSONAS_FILE`.
- Known constraints:
  - Webhook permission required for persona‑named/avatared posts; otherwise uses bot posts with name prefix.
  - Under Bazel, the bundled YAML may not resolve at runtime on some setups; set `ATHENA_PERSONAS_FILE` to an absolute path as needed.
  - Tests should pass locally; network‑restricted sandboxes may prevent Bazel from fetching toolchains/wheels.
- Near‑term goals (pick‑up items):
  - Add argument validation/error messages for invalid persona names and out‑of‑range `rounds`/`temperature`.
  - Add unit tests for concise mode (token/temperature clamps) and webhook fallback path.
  - Expose model choice/provider via env/flags (e.g., `OPENAI_MODEL`; allow alternate providers behind the `ChatModel` protocol).
  - Improve channel topic metadata to include message counts and completion status; add `/bots_end` to stop a running agent debate.
  - Rate‑limit `/bots_debate` per guild to avoid spam; guardrails for maximum concurrent debates.

## Pick‑Up Checklist

- `bazel test //apps/athena-bot:bot_tests` should pass.
- Ensure `.env` has `DISCORD_BOT_TOKEN` (and `DISCORD_GUILD_ID` for fast sync).
- For agent debates: set `OPENAI_API_KEY`; optionally `OPENAI_MODEL` and `ATHENA_PERSONAS_FILE`.
- Verify Discord role has: Manage Channels, Manage Permissions, Send Messages; add Manage Webhooks for persona styling.
- Sanity run: `/bots_personas`, then `/bots_debate topic:"<your topic>" rounds:2 concise:true` and confirm alternating posts.

## Troubleshooting

- Privileged intents error: Enable Message Content Intent.
- Commands not visible: Ensure the bot was invited with `applications.commands` scope; use `/sync`. With `DISCORD_GUILD_ID` set, commands are guild-scoped and appear instantly.
- Missing permissions on Accept: Make sure the bot role is above participants and has "Manage Channels" and "Send Messages".
