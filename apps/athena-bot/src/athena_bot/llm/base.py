from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable, Protocol


class Role(str, Enum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"


@dataclass(frozen=True)
class ChatMessage:
    role: Role
    content: str


class ChatModel(Protocol):
    def generate(
        self,
        messages: Iterable[ChatMessage],
        *,
        temperature: float = 0.7,
        max_output_tokens: int | None = None,
    ) -> str:
        """Synchronous generation interface for simple turn-based chat.

        Implementations should be side-effect free and allow repeated calls.
        The first system message (if present) can be surfaced to the model.
        """
        ...
