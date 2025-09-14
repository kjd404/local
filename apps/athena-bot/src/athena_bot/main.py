import logging
import sys

from athena_bot.config import AthenaConfig
from athena_bot.bot import AthenaBot


def run() -> None:
    logging.basicConfig(
        level=logging.INFO,
        stream=sys.stdout,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    config = AthenaConfig.from_env()
    bot = AthenaBot(guild_id=config.guild_id)
    bot.client.run(config.token)


if __name__ == "__main__":
    run()
