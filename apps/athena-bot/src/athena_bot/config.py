from dataclasses import dataclass
from typing import Optional
import os


@dataclass(frozen=True)
class AthenaConfig:
    token: str
    guild_id: Optional[int] = None

    @staticmethod
    def from_env() -> "AthenaConfig":
        token = os.environ.get("DISCORD_BOT_TOKEN", "").strip()
        if not token:
            raise SystemExit("DISCORD_BOT_TOKEN is required (set in .env or shell)")
        gid_raw = os.environ.get("DISCORD_GUILD_ID", "").strip()
        guild_id = int(gid_raw) if gid_raw.isdigit() else None
        return AthenaConfig(token=token, guild_id=guild_id)
