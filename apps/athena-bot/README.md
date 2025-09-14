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
- For debate moderation, enable the "Message Content Intent" for your bot in the Developer Portal, and ensure the bot role has "Manage Channels" and "Manage Permissions" in your server.
  - The bot also needs to be able to Send Messages in created debate channels. The app now adds a bot-specific channel overwrite to allow View and Send; still ensure the bot’s role is positioned above participants and has those base permissions.

## Command Sync
- With `DISCORD_GUILD_ID` set, commands are registered for that guild and appear instantly. Without it, commands are global and may take up to ~1 minute to propagate.
- Use `/sync` (requires Manage Server) to force-refresh commands.

## Docker Helpers
- Build image: `bazel run //apps/athena-bot/docker:build_image -- --tag=athena-bot:latest`
- Run container: `bazel run //apps/athena-bot/docker:run_container -- --image=athena-bot:latest`
- Logs: `bazel run //apps/athena-bot/docker:logs`
- Stop: `bazel run //apps/athena-bot/docker:stop_container`
- Status: `bazel run //apps/athena-bot/docker:status`

## Notes
- Tests use pytest; they avoid network calls and only validate configuration and client wiring.
- The Docker wrapper reads `.env` and forwards `DISCORD_BOT_TOKEN` into the container.

## Slash Commands
- `/ping` – Health check.
- `/challenge @user <topic> [private]` – Challenge a member to a debate. The challenged user can Accept/Decline via buttons. Use `private=true` to create a hidden channel; default is public read-only for others.
- `/debate_end` – End the current debate; makes the debate channel read-only.
- `/sync` – Force-resync application commands (admin only; Manage Server).

## Debate Flow
- When accepted, the bot creates a text channel named like `challenged-vs-challenger-topic`.
- Only the two participants may send messages; others can read but cannot type (unless `private=true`, in which case only participants can view).
- The challenger goes first. Each message automatically hands the turn to the other participant by flipping send permissions.
- Either participant can end the debate with `/debate_end` which locks the channel (read-only).
- The bot posts a small system prompt on each turn change: `Your turn, @user`.

## Persistence
- Debate state is stored in the channel topic (IDs + status). On startup, the bot restores debates and infers whose turn it is from channel permissions. No external database required.
