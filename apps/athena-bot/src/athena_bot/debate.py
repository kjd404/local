from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from enum import Enum, auto
from typing import Dict, Optional

import discord


log = logging.getLogger("athena_bot.debate")

TOPIC_PREFIX = "[athena-debate]"


def _encode_topic(
    challenger_id: int, challenged_id: int, topic: str, status: "DebateStatus"
) -> str:
    safe_topic = topic.replace("|", "/")[:200]
    return f"{TOPIC_PREFIX} challenger={challenger_id}|challenged={challenged_id}|status={status.name}|topic={safe_topic}"


def _parse_topic(topic: Optional[str]) -> Optional[dict]:
    if not topic or not topic.startswith(TOPIC_PREFIX):
        return None
    try:
        _, rest = topic.split(" ", 1)
        parts = dict(kv.split("=", 1) for kv in rest.split("|") if "=" in kv)
        return parts
    except Exception:
        return None


class DebateStatus(Enum):
    PENDING = auto()
    ACTIVE = auto()
    ENDED = auto()


@dataclass
class Debate:
    challenger_id: int
    challenged_id: int
    topic: str
    channel_id: int
    current_turn_id: int
    status: DebateStatus

    @property
    def participants(self) -> set[int]:
        return {self.challenger_id, self.challenged_id}


def _slug(text: str, max_len: int = 40) -> str:
    # Basic slugify: lowercase, alnum and hyphens only, collapse dashes
    import re

    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text[:max_len].strip("-") or "topic"


class DebateManager:
    def __init__(self) -> None:
        self._by_channel: Dict[int, Debate] = {}
        # Lock to avoid race conditions when flipping turns rapidly
        self._locks: Dict[int, asyncio.Lock] = {}

    def get(self, channel_id: int) -> Optional[Debate]:
        return self._by_channel.get(channel_id)

    def is_debate_channel(self, channel_id: int) -> bool:
        return channel_id in self._by_channel

    async def create_and_start(
        self,
        guild: discord.Guild,
        challenger: discord.Member,
        challenged: discord.Member,
        topic: str,
        private: bool = False,
    ) -> discord.TextChannel:
        channel_name = f"{_slug(challenged.display_name)}-vs-{_slug(challenger.display_name)}-{_slug(topic, 30)}"
        channel_name = channel_name[:95]  # Discord channel name hard cap ~100

        # Ensure the bot itself can view and send in the channel, even if @everyone is denied
        me = getattr(guild, "me", None)
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(
                view_channel=(not private),
                read_messages=(not private),
                send_messages=False,
            ),
            challenger: discord.PermissionOverwrite(
                view_channel=True, send_messages=True
            ),
            challenged: discord.PermissionOverwrite(
                view_channel=True, send_messages=False
            ),
        }
        if me is not None:
            overwrites[me] = discord.PermissionOverwrite(
                view_channel=True, send_messages=True
            )

        channel = await guild.create_text_channel(
            name=channel_name,
            overwrites=overwrites,
            reason=f"Debate between {challenger.display_name} and {challenged.display_name}",
        )

        debate = Debate(
            challenger_id=challenger.id,
            challenged_id=challenged.id,
            topic=topic,
            channel_id=channel.id,
            current_turn_id=challenger.id,  # Challenger starts
            status=DebateStatus.ACTIVE,
        )
        self._by_channel[channel.id] = debate
        self._locks[channel.id] = asyncio.Lock()

        # Store metadata in channel topic to enable restoration after restarts
        try:
            await channel.edit(
                topic=_encode_topic(challenger.id, challenged.id, topic, debate.status)
            )
        except Exception as e:
            log.warning("Failed to set channel topic for %s: %s", channel.id, e)

        await channel.send(
            f"Debate started: {challenger.mention} vs {challenged.mention} — Topic: “{topic}”.\n"
            f"{challenger.mention} goes first. Each message hands the turn to the other speaker.\n"
            f"Use /debate_end to finish."
        )
        return channel

    async def flip_turn(
        self, channel: discord.TextChannel, author: discord.Member
    ) -> None:
        debate = self._by_channel.get(channel.id)
        if not debate or debate.status is not DebateStatus.ACTIVE:
            return
        if author.id != debate.current_turn_id:
            return

        lock = self._locks[channel.id]
        async with lock:
            # Double-check inside lock
            debate = self._by_channel.get(channel.id)
            if not debate or debate.status is not DebateStatus.ACTIVE:
                return
            if author.id != debate.current_turn_id:
                return

            # Determine next speaker
            next_speaker_id = (
                debate.challenged_id
                if author.id == debate.challenger_id
                else debate.challenger_id
            )

            # Fetch Member objects for permission updates
            guild = channel.guild
            current = guild.get_member(debate.current_turn_id)
            nxt = guild.get_member(next_speaker_id)
            # Fallback to API fetch if not cached (works without Members intent for single fetches)
            if not current:
                try:
                    current = await guild.fetch_member(debate.current_turn_id)
                except Exception as e:
                    log.warning(
                        "fetch_member failed for current in %s: %s", channel.id, e
                    )
            if not nxt:
                try:
                    nxt = await guild.fetch_member(next_speaker_id)
                except Exception as e:
                    log.warning("fetch_member failed for next in %s: %s", channel.id, e)
            if not current or not nxt:
                log.warning("Could not resolve members to flip turn in %s", channel.id)
                return

            # Update permissions: enable next, then disable current
            try:
                # Grant next speaker before revoking current, to minimize gaps
                await channel.set_permissions(nxt, send_messages=True)
                await channel.set_permissions(current, send_messages=False)
            except Exception as e:
                log.exception("Failed to update permissions in %s: %s", channel.id, e)
                return

            debate.current_turn_id = next_speaker_id
            log.info("Turn flipped in #%s: now %s", channel.name, nxt.display_name)
            # Notify next speaker
            try:
                await channel.send(f"Your turn, {nxt.mention}")
            except Exception as e:
                log.warning("Failed to post turn message in %s: %s", channel.id, e)

    async def end_debate(self, channel: discord.TextChannel) -> None:
        debate = self._by_channel.get(channel.id)
        if not debate or debate.status is DebateStatus.ENDED:
            return
        debate.status = DebateStatus.ENDED
        guild = channel.guild

        # Lock the channel for everyone
        try:
            await channel.set_permissions(guild.default_role, send_messages=False)
            # Also lock both participants explicitly
            p1 = guild.get_member(debate.challenger_id)
            p2 = guild.get_member(debate.challenged_id)
            if p1:
                await channel.set_permissions(p1, send_messages=False)
            if p2:
                await channel.set_permissions(p2, send_messages=False)
        except Exception as e:
            log.exception("Failed to lock channel %s on end: %s", channel.id, e)

        try:
            await channel.send("Debate ended. Channel is now read-only.")
        except Exception:
            pass
        # Update topic to reflect ended status
        try:
            await channel.edit(
                topic=_encode_topic(
                    debate.challenger_id,
                    debate.challenged_id,
                    debate.topic,
                    debate.status,
                )
            )
        except Exception as e:
            log.warning("Failed to update topic on end for %s: %s", channel.id, e)

    def is_participant(self, channel_id: int, user_id: int) -> bool:
        debate = self._by_channel.get(channel_id)
        return bool(debate and user_id in debate.participants)

    async def restore_from_guild(self, guild: discord.Guild) -> int:
        restored = 0
        for channel in getattr(guild, "text_channels", []):
            meta = _parse_topic(getattr(channel, "topic", None))
            if not meta:
                continue
            try:
                challenger_id = int(meta.get("challenger", "0"))
                challenged_id = int(meta.get("challenged", "0"))
                topic = meta.get("topic", "")
                status_name = meta.get("status", "ACTIVE")
                status = (
                    DebateStatus[status_name]
                    if status_name in DebateStatus.__members__
                    else DebateStatus.ACTIVE
                )
            except Exception:
                continue

            # Determine current turn from channel permissions
            current_turn_id = challenger_id
            try:
                ch_member = guild.get_member(challenger_id) or await guild.fetch_member(
                    challenger_id
                )
                dg_member = guild.get_member(challenged_id) or await guild.fetch_member(
                    challenged_id
                )
                if ch_member and dg_member and hasattr(channel, "permissions_for"):
                    ch_can = channel.permissions_for(ch_member).send_messages
                    dg_can = channel.permissions_for(dg_member).send_messages
                    if ch_can and not dg_can:
                        current_turn_id = challenger_id
                    elif dg_can and not ch_can:
                        current_turn_id = challenged_id
            except Exception as e:
                log.debug("Permission inference failed for %s: %s", channel.id, e)

            debate = Debate(
                challenger_id=challenger_id,
                challenged_id=challenged_id,
                topic=topic,
                channel_id=channel.id,
                current_turn_id=current_turn_id,
                status=status,
            )
            self._by_channel[channel.id] = debate
            self._locks[channel.id] = asyncio.Lock()
            restored += 1
        if restored:
            log.info("Restored %s debate(s) from guild %s", restored, guild.id)
        return restored
