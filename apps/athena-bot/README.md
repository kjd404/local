# Athena Bot

Discord bot (athena-bot) built in Python. Reads configuration from environment and can be run via Bazel or Docker.

## Build & Run
- Run locally: `bazel run //apps/athena-bot:bot`
- Run tests: `bazel test //apps/athena-bot:bot_tests`

## Environment
- `DISCORD_BOT_TOKEN` – Discord bot token (required). Place in `.env` or export in your shell.
- `DISCORD_GUILD_ID` – Optional numeric guild ID to speed command sync during development.

## Discord Setup
- In the Discord Developer Portal, create or open your app (athena-bot), add a Bot, and copy its token into `.env` as `DISCORD_BOT_TOKEN`.
- Invite the bot to your server using an OAuth2 URL with `bot` and `applications.commands` scopes.
- No inbound ports are required; the bot connects outbound to Discord gateways. Docker needs outbound internet access.

## Slash Commands
- A `/ping` command is registered at startup and synced on ready. Global sync can take up to ~1 minute initially; set `DISCORD_GUILD_ID` to sync faster to a single server while developing.

## Docker Helpers
- Build image: `bazel run //apps/athena-bot/docker:build_image -- --tag=athena-bot:latest`
- Run container: `bazel run //apps/athena-bot/docker:run_container -- --image=athena-bot:latest`
- Logs: `bazel run //apps/athena-bot/docker:logs`
- Stop: `bazel run //apps/athena-bot/docker:stop_container`
- Status: `bazel run //apps/athena-bot/docker:status`

## Notes
- Tests use pytest; they avoid network calls and only validate configuration and client wiring.
- The Docker wrapper reads `.env` and forwards `DISCORD_BOT_TOKEN` into the container.
