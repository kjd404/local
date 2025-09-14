import discord

from athena_bot.bot import AthenaBot


def test_build_client_returns_client_and_tree():
    bot = AthenaBot()
    client = bot.client
    assert isinstance(client, discord.Client)
    assert isinstance(client.intents, discord.Intents)
    # Slash command should be registered (not synced until ready)
    commands = [c.name for c in bot.tree.get_commands()]
    assert "ping" in commands
