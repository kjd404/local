import logging
from typing import Optional

import discord
from discord import app_commands


log = logging.getLogger("athena_bot")


class AthenaBot:
    def __init__(
        self, intents: Optional[discord.Intents] = None, guild_id: Optional[int] = None
    ) -> None:
        # Enable message content intent for turn-based moderation.
        # Note: This must also be enabled for the bot in the Discord Developer Portal.
        self._intents = intents or discord.Intents.default()
        # Ensure we receive message create events and content for turn flipping
        self._intents.messages = True
        self._intents.message_content = True
        self._client = discord.Client(intents=self._intents)
        self._tree = app_commands.CommandTree(self._client)
        self._guild_id = guild_id
        # Debate manager handles state and permission flips
        from athena_bot.debate import DebateManager

        self._debates = DebateManager()
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
                    guild_obj = discord.Object(id=self._guild_id)
                    await self._tree.sync(guild=guild_obj)
                    log.info(
                        "Athena bot ready as %s; guild commands synced for %s",
                        self._client.user,
                        self._guild_id,
                    )
                    local_cmds = [
                        c.name for c in self._tree.get_commands(guild=guild_obj)
                    ]
                    try:
                        fetched = await self._tree.fetch_commands(guild=guild_obj)
                        fetched_names = ", ".join(c.name for c in fetched)
                    except Exception as e:
                        fetched_names = f"<fetch failed: {e}>"
                    log.info(
                        "Registered commands (guild-local): %s; fetched from API: %s",
                        ", ".join(local_cmds),
                        fetched_names,
                    )
                    # Attempt to restore debates from existing channels
                    guild = self._client.get_guild(self._guild_id)
                    if guild:
                        restored = await self._debates.restore_from_guild(guild)
                        log.info("Restored debates: %s", restored)
                else:
                    await self._tree.sync()
                    log.info(
                        "Athena bot ready as %s; global commands synced",
                        self._client.user,
                    )
                    cmds = [c.name for c in self._tree.get_commands()]
                    log.info("Registered commands (global): %s", ", ".join(cmds))
            except Exception as e:
                log.exception("Failed to sync commands: %s", e)

        @self._client.event
        async def on_message(message: discord.Message):
            # Ignore bots and DMs
            if message.author.bot or not message.guild or not message.channel:
                return
            # Only act in debate channels and after a participant sends a message
            if self._debates.is_debate_channel(message.channel.id):
                log.debug(
                    "msg in debate channel %s from %s",
                    message.channel.id,
                    message.author.id,
                )
                # Flip turn after a participant sends any message
                if isinstance(message.channel, discord.TextChannel) and isinstance(
                    message.author, discord.Member
                ):
                    await self._debates.flip_turn(message.channel, message.author)

    def _register_commands(self) -> None:
        # If a guild_id is provided, register commands scoped to that guild for instant availability
        guild_obj = discord.Object(id=self._guild_id) if self._guild_id else None

        if guild_obj:

            @self._tree.command(
                name="ping", description="Health check", guild=guild_obj
            )
            async def ping(interaction: discord.Interaction):
                await interaction.response.send_message("Pong!", ephemeral=True)
        else:

            @self._tree.command(name="ping", description="Health check")
            async def ping(interaction: discord.Interaction):
                await interaction.response.send_message("Pong!", ephemeral=True)

        # Challenge command: start a debate request with Accept/Decline buttons
        if guild_obj:

            @self._tree.command(
                name="challenge",
                description="Challenge a member to a debate on a topic",
                guild=guild_obj,
            )
            @app_commands.describe(
                target="User to challenge",
                topic="Debate topic",
                private="Create a private (hidden) channel",
            )
            async def challenge(
                interaction: discord.Interaction,
                target: discord.Member,
                topic: str,
                private: bool = False,
            ):
                assert interaction.guild is not None, "Guild context required"

                challenger = interaction.user
                if not isinstance(challenger, discord.Member):
                    await interaction.response.send_message(
                        "This command can only be used in a server.", ephemeral=True
                    )
                    return

                if target.id == challenger.id:
                    await interaction.response.send_message(
                        "You cannot challenge yourself.", ephemeral=True
                    )
                    return

                view = _ChallengeView(self, challenger, target, topic, private)

                await interaction.response.send_message(
                    f"{target.mention}, you have been challenged by {challenger.mention}!\n"
                    f"Topic: “{topic}”.\n"
                    f"Do you accept?",
                    view=view,
                    ephemeral=False,
                )
        else:

            @self._tree.command(
                name="challenge",
                description="Challenge a member to a debate on a topic",
            )
            @app_commands.describe(
                target="User to challenge",
                topic="Debate topic",
                private="Create a private (hidden) channel",
            )
            async def challenge(
                interaction: discord.Interaction,
                target: discord.Member,
                topic: str,
                private: bool = False,
            ):
                assert interaction.guild is not None, "Guild context required"

                challenger = interaction.user
                if not isinstance(challenger, discord.Member):
                    await interaction.response.send_message(
                        "This command can only be used in a server.", ephemeral=True
                    )
                    return

                if target.id == challenger.id:
                    await interaction.response.send_message(
                        "You cannot challenge yourself.", ephemeral=True
                    )
                    return

                # Create a confirmation view restricted to the challenged user
                view = _ChallengeView(self, challenger, target, topic, private)

                await interaction.response.send_message(
                    f"{target.mention}, you have been challenged by {challenger.mention}!\n"
                    f"Topic: “{topic}”.\n"
                    f"Do you accept?",
                    view=view,
                    ephemeral=False,
                )

        # End debate command: can be used by either participant in the debate channel
        if guild_obj:

            @self._tree.command(
                name="debate_end",
                description="End the current debate and lock the channel",
                guild=guild_obj,
            )
            async def debate_end(interaction: discord.Interaction):
                channel = interaction.channel
                if not isinstance(channel, discord.TextChannel):
                    await interaction.response.send_message(
                        "Use this in the debate channel.", ephemeral=True
                    )
                    return

                if not self._debates.is_debate_channel(channel.id):
                    await interaction.response.send_message(
                        "This is not a debate channel.", ephemeral=True
                    )
                    return

                user = interaction.user
                if not isinstance(
                    user, discord.Member
                ) or not self._debates.is_participant(channel.id, user.id):
                    await interaction.response.send_message(
                        "Only debate participants may end the debate.", ephemeral=True
                    )
                    return

                await self._debates.end_debate(channel)
                await interaction.response.send_message(
                    "Debate ended. The channel is now read-only.", ephemeral=True
                )
        else:

            @self._tree.command(
                name="debate_end",
                description="End the current debate and lock the channel",
            )
            async def debate_end(interaction: discord.Interaction):
                channel = interaction.channel
                if not isinstance(channel, discord.TextChannel):
                    await interaction.response.send_message(
                        "Use this in the debate channel.", ephemeral=True
                    )
                    return

                if not self._debates.is_debate_channel(channel.id):
                    await interaction.response.send_message(
                        "This is not a debate channel.", ephemeral=True
                    )
                    return

                user = interaction.user
                if not isinstance(
                    user, discord.Member
                ) or not self._debates.is_participant(channel.id, user.id):
                    await interaction.response.send_message(
                        "Only debate participants may end the debate.", ephemeral=True
                    )
                    return

                await self._debates.end_debate(channel)
                await interaction.response.send_message(
                    "Debate ended. The channel is now read-only.", ephemeral=True
                )

        # Admin-only: force sync commands (useful if commands seem missing)
        # Restrict to users with Manage Guild permission
        if guild_obj:

            @self._tree.command(
                name="sync",
                description="Force-resync application commands",
                guild=guild_obj,
            )
            @app_commands.checks.has_permissions(manage_guild=True)
            async def sync(interaction: discord.Interaction):
                try:
                    await self._tree.sync(guild=guild_obj)
                    await interaction.response.send_message(
                        "Commands synced for this guild.", ephemeral=True
                    )
                except Exception as e:
                    await interaction.response.send_message(
                        f"Sync failed: {e}", ephemeral=True
                    )
        else:

            @self._tree.command(
                name="sync", description="Force-resync application commands"
            )
            @app_commands.checks.has_permissions(manage_guild=True)
            async def sync(interaction: discord.Interaction):
                try:
                    await self._tree.sync()
                    await interaction.response.send_message(
                        "Global commands synced (may take time to appear).",
                        ephemeral=True,
                    )
                except Exception as e:
                    await interaction.response.send_message(
                        f"Sync failed: {e}", ephemeral=True
                    )


class _ChallengeView(discord.ui.View):
    def __init__(
        self,
        bot: AthenaBot,
        challenger: discord.Member,
        challenged: discord.Member,
        topic: str,
        private: bool,
    ) -> None:
        super().__init__(timeout=60 * 5)
        self._bot = bot
        self._challenger = challenger
        self._challenged = challenged
        self._topic = topic
        self._private = private

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        # Only the challenged user can interact with the buttons
        if interaction.user.id != self._challenged.id:
            await interaction.response.send_message(
                "Only the challenged user can respond to this.", ephemeral=True
            )
            return False
        return True

    @discord.ui.button(label="Accept", style=discord.ButtonStyle.success)
    async def accept(self, interaction: discord.Interaction, button: discord.ui.Button):
        if not interaction.guild:
            await interaction.response.send_message(
                "Guild context required.", ephemeral=True
            )
            return
        # Create the debate channel and start the debate
        try:
            channel = await self._bot._debates.create_and_start(
                interaction.guild,
                self._challenger,
                self._challenged,
                self._topic,
                private=self._private,
            )
        except discord.Forbidden:
            await interaction.response.send_message(
                "I’m missing permissions to send or manage permissions in the new channel. "
                "Please ensure my role has Manage Channels and Send Messages, and sits above the participants.",
                ephemeral=True,
            )
            return

        await interaction.response.edit_message(
            content=(
                f"Accepted. Debate channel created: {channel.mention} — "
                f"{self._challenger.mention} goes first."
            ),
            view=None,
        )
        self.stop()

    @discord.ui.button(label="Decline", style=discord.ButtonStyle.danger)
    async def decline(
        self, interaction: discord.Interaction, button: discord.ui.Button
    ):
        await interaction.response.edit_message(
            content=f"{self._challenged.mention} declined the debate.", view=None
        )
        self.stop()
