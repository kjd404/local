from __future__ import annotations

import asyncio
import logging
from typing import Optional

import discord

from .llm import ChatMessage, ChatModel, Role
from .personas import Persona


log = logging.getLogger("athena_bot.agent_debate")

AGENT_TOPIC_PREFIX = "[athena-agents]"


"""
Persona definitions are loaded from config (athena_bot/personas.py).
"""


class AgentDebateOrchestrator:
    """Runs an LLM vs LLM debate in a Discord text channel.

    It uses a channel webhook (if available) to post with persona names; falls back
    to normal bot messages with a name prefix when webhooks are unavailable.
    """

    def __init__(self, *, delay_seconds: float = 1.0) -> None:
        self._delay = max(0.0, delay_seconds)

    async def create_channel(
        self,
        guild: discord.Guild,
        topic: str,
        private: bool,
    ) -> discord.TextChannel:
        # Allow only the bot to send; others read-only (or hidden if private)
        me = getattr(guild, "me", None)
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(
                view_channel=(not private),
                read_messages=(not private),
                send_messages=False,
            )
        }
        if me is not None:
            overwrites[me] = discord.PermissionOverwrite(
                view_channel=True, send_messages=True
            )

        # Keep name short and informative
        safe = _slug(topic, 30)
        name = f"agents-{safe}"[:95]
        channel = await guild.create_text_channel(
            name=name,
            overwrites=overwrites,
            reason=f"Agent debate: {topic}",
        )
        return channel

    async def ensure_webhook(
        self, channel: discord.TextChannel
    ) -> Optional[discord.Webhook]:
        try:
            hooks = await channel.webhooks()
            for h in hooks:
                if h.name == "athena-agents":
                    return h
            return await channel.create_webhook(name="athena-agents")
        except Exception as e:
            log.debug("No webhook available in %s: %s", channel.id, e)
            return None

    async def run_debate(
        self,
        channel: discord.TextChannel,
        *,
        topic: str,
        left: Persona,
        right: Persona,
        left_model: ChatModel,
        right_model: ChatModel,
        rounds: int = 6,
        temperature: float = 0.7,
        max_output_tokens: Optional[int] = None,
        concise: bool = False,
    ) -> None:
        rounds = max(1, min(20, rounds))
        webhook = await self.ensure_webhook(channel)

        async def post(name: str, content: str, avatar: Optional[str]) -> None:
            if webhook is not None:
                try:
                    await webhook.send(
                        content, username=name, avatar_url=avatar, wait=True
                    )
                    return
                except Exception as e:
                    log.debug("Webhook send failed for %s: %s", channel.id, e)
            await channel.send(f"**{name}:** {content}")

        # Shared conversation history for both sides
        if concise:
            style_msg = (
                "You are participating in a respectful, non-performative debate."
                " Style: plain language; 3–4 sentences (≈60–120 words); be direct and specific."
                " Avoid greeting, theatrics, exclamations, slogans, or addressing the audience."
                " Refer to concrete facts/examples when possible."
                " Do not restate your identity after your first message."
            )
        else:
            style_msg = (
                "You are participating in a respectful, non-performative debate."
                " Style: plain language; 3–6 sentences (≈80–150 words)."
                " Avoid greeting, theatrics, exclamations, slogans, or addressing the audience."
                " Refer to concrete facts/examples when possible."
                " Do not restate your identity after your first message."
            )

        history: list[ChatMessage] = [ChatMessage(role=Role.SYSTEM, content=style_msg)]

        await channel.edit(
            topic=(
                f"{AGENT_TOPIC_PREFIX} left={left.name}|right={right.name}|topic={topic}"
            )
        )

        # Opening statements
        left_open = await self._gen(
            left_model,
            [
                ChatMessage(Role.SYSTEM, left.system_prompt),
                *history,
                ChatMessage(
                    Role.USER,
                    (
                        f"Topic: {topic}. Opening statement: briefly introduce yourself once in the first line,"
                        " then provide a short opening. No headings or bold."
                        + (" Keep it to 3–4 sentences." if concise else "")
                    ),
                ),
            ],
            temperature=temperature,
            max_output_tokens=max_output_tokens,
        )
        await post(left.name, left_open, left.avatar_url)
        history.append(ChatMessage(Role.ASSISTANT, left_open))
        await asyncio.sleep(self._delay)

        right_open = await self._gen(
            right_model,
            [
                ChatMessage(Role.SYSTEM, right.system_prompt),
                *history,
                ChatMessage(
                    Role.USER,
                    (
                        "Provide your opening statement: introduce yourself once in the first line,"
                        " and challenge prior claims naturally. No headings; no repeated identity later."
                        + (" Keep it to 3–4 sentences." if concise else "")
                    ),
                ),
            ],
            temperature=temperature,
            max_output_tokens=max_output_tokens,
        )
        await post(right.name, right_open, right.avatar_url)
        history.append(ChatMessage(Role.ASSISTANT, right_open))
        await asyncio.sleep(self._delay)

        # Rebuttal rounds
        for i in range(rounds - 1):
            left_reply = await self._gen(
                left_model,
                [
                    ChatMessage(Role.SYSTEM, left.system_prompt),
                    *history,
                    ChatMessage(
                        Role.USER,
                        (
                            "Offer a direct rebuttal and advance your argument."
                            " No greeting or introduction; do not restate your identity."
                            + (" Keep it to 3–4 sentences." if concise else "")
                        ),
                    ),
                ],
                temperature=temperature,
                max_output_tokens=max_output_tokens,
            )
            await post(left.name, left_reply, left.avatar_url)
            history.append(ChatMessage(Role.ASSISTANT, left_reply))
            await asyncio.sleep(self._delay)

            right_reply = await self._gen(
                right_model,
                [
                    ChatMessage(Role.SYSTEM, right.system_prompt),
                    *history,
                    ChatMessage(
                        Role.USER,
                        (
                            "Respond directly and strengthen your position with specifics."
                            " No greeting or introduction; do not restate your identity."
                            + (" Keep it to 3–4 sentences." if concise else "")
                        ),
                    ),
                ],
                temperature=temperature,
                max_output_tokens=max_output_tokens,
            )
            await post(right.name, right_reply, right.avatar_url)
            history.append(ChatMessage(Role.ASSISTANT, right_reply))
            await asyncio.sleep(self._delay)

        # Closing statements
        left_close = await self._gen(
            left_model,
            [
                ChatMessage(Role.SYSTEM, left.system_prompt),
                *history,
                ChatMessage(
                    Role.USER,
                    (
                        "Provide a short closing. No greeting or re-introduction; summarize key trade-offs succinctly."
                        + (" Keep it to 3–4 sentences." if concise else "")
                    ),
                ),
            ],
            temperature=temperature,
            max_output_tokens=max_output_tokens,
        )
        await post(left.name, left_close, left.avatar_url)
        history.append(ChatMessage(Role.ASSISTANT, left_close))
        await asyncio.sleep(self._delay)

        right_close = await self._gen(
            right_model,
            [
                ChatMessage(Role.SYSTEM, right.system_prompt),
                *history,
                ChatMessage(
                    Role.USER,
                    (
                        "Provide a short closing. No greeting or re-introduction; end crisply."
                        + (" Keep it to 3–4 sentences." if concise else "")
                    ),
                ),
            ],
            temperature=temperature,
            max_output_tokens=max_output_tokens,
        )
        await post(right.name, right_close, right.avatar_url)

    async def _gen(
        self,
        model: ChatModel,
        messages: list[ChatMessage],
        *,
        temperature: float,
        max_output_tokens: Optional[int],
    ) -> str:
        loop = asyncio.get_running_loop()

        def _call():
            return model.generate(
                messages, temperature=temperature, max_output_tokens=max_output_tokens
            )

        return await loop.run_in_executor(None, _call)


def _slug(text: str, max_len: int = 40) -> str:
    import re

    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text[:max_len].strip("-") or "topic"
