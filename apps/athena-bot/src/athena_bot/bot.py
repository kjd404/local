import logging
from typing import Optional

import discord
from discord import app_commands


log = logging.getLogger("athena_bot")


class AthenaBot:
    def __init__(
        self, intents: Optional[discord.Intents] = None, guild_id: Optional[int] = None
    ) -> None:
        self._intents = intents or discord.Intents.default()
        self._client = discord.Client(intents=self._intents)
        self._tree = app_commands.CommandTree(self._client)
        self._guild_id = guild_id
        self._register_events()
        self._register_commands()

    @property
    def client(self) -> discord.Client:
        return self._client

    @property
    def tree(self) -> app_commands.CommandTree:
        return self._tree

    def _register_events(self) -> None:
        @self._client.event
        async def on_ready():
            try:
                if self._guild_id:
                    await self._tree.sync(guild=discord.Object(id=self._guild_id))
                    log.info(
                        "Athena bot ready as %s; guild commands synced for %s",
                        self._client.user,
                        self._guild_id,
                    )
                else:
                    await self._tree.sync()
                    log.info(
                        "Athena bot ready as %s; global commands synced",
                        self._client.user,
                    )
            except Exception as e:
                log.exception("Failed to sync commands: %s", e)

    def _register_commands(self) -> None:
        @self._tree.command(name="ping", description="Health check")
        async def ping(interaction: discord.Interaction):
            await interaction.response.send_message("Pong!", ephemeral=True)
