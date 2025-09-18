import asyncio
from typing import List, Optional


from athena_bot.agent_debate import AgentDebateOrchestrator
from athena_bot.personas import Persona
from athena_bot.llm import ChatModel


class FakeWebhook:
    def __init__(self) -> None:
        self.sent: List[str] = []

    async def send(
        self, content: str, username: str, avatar_url: Optional[str], wait: bool = True
    ):
        self.sent.append(f"{username}: {content}")


class FakeGuild:
    def __init__(self) -> None:
        class _Role:
            id = 0
            name = "@everyone"

        self.default_role = _Role()
        self.me = object()

    async def create_text_channel(self, name, overwrites, reason):
        return FakeChannel(name)


class FakeChannel:
    def __init__(self, name: str) -> None:
        self.name = name
        self._messages: List[str] = []
        self.topic: Optional[str] = None
        self._webhook: Optional[FakeWebhook] = None

    async def edit(self, **kwargs):
        if "topic" in kwargs:
            self.topic = kwargs["topic"]

    async def send(self, content: str):
        self._messages.append(content)

    async def webhooks(self):
        return []

    async def create_webhook(self, name: str):
        self._webhook = FakeWebhook()
        return self._webhook


class FakeModel(ChatModel):
    def __init__(self, tag: str) -> None:
        self.tag = tag
        self.i = 0

    def generate(
        self,
        messages,
        *,
        temperature: float = 0.7,
        max_output_tokens: int | None = None,
    ) -> str:  # type: ignore[override]
        self.i += 1
        return f"{self.tag}-{self.i}"


def test_orchestrator_posts_alternating_messages():
    async def run():
        guild = FakeGuild()
        orch = AgentDebateOrchestrator(delay_seconds=0)
        ch = await orch.create_channel(guild, topic="Test Topic", private=False)

        left = Persona(name="Demosthenes", system_prompt="be left")
        right = Persona(name="Aeschines", system_prompt="be right")

        m_left = FakeModel("L")
        m_right = FakeModel("R")

        await orch.run_debate(
            ch,
            topic="X",
            left=left,
            right=right,
            left_model=m_left,
            right_model=m_right,
            rounds=2,
        )

        # With webhook available, no fallback messages; validate webhook content
        assert ch._webhook is not None
        sent = ch._webhook.sent
        # 2 rounds -> 2 openings + 2*1 rebuttals per side + 2 closings = 6 total messages
        assert len(sent) == 6
        assert sent[0].startswith("Demosthenes: L-1")
        assert sent[1].startswith("Aeschines: R-1")
        assert sent[2].startswith("Demosthenes: L-2")
        assert sent[3].startswith("Aeschines: R-2")
        assert sent[4].startswith("Demosthenes: L-3")
        assert sent[5].startswith("Aeschines: R-3")

    asyncio.run(run())
