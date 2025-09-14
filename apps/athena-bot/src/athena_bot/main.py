import logging
import sys

from athena_bot.config import AthenaConfig
from athena_bot.bot import AthenaBot
import discord


def run() -> None:
    logging.basicConfig(
        level=logging.INFO,
        stream=sys.stdout,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    config = AthenaConfig.from_env()
    bot = AthenaBot(guild_id=config.guild_id)
    try:
        bot.client.run(config.token)
    except discord.errors.PrivilegedIntentsRequired:
        logging.error(
            "Privileged intents required: enable 'Message Content Intent' for your bot in the Discord Developer Portal (Bot -> Privileged Gateway Intents)."
        )
        raise


if __name__ == "__main__":
    run()
