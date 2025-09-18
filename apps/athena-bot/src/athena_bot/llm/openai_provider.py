from __future__ import annotations

import os
from typing import Iterable, Optional

from .base import ChatMessage, ChatModel


class OpenAIChatModel(ChatModel):
    """Thin wrapper around the OpenAI Chat Completions API.

    Import of the OpenAI SDK is deferred to runtime so tests and builds
    without the package installed still succeed when this provider is unused.
    """

    def __init__(
        self,
        model: Optional[str] = None,
        api_key: Optional[str] = None,
    ) -> None:
        self._model = model or os.getenv("OPENAI_MODEL", "gpt-4o-mini")
        self._api_key = api_key or os.getenv("OPENAI_API_KEY", "")
        if not self._api_key:
            raise RuntimeError("OPENAI_API_KEY is required to use OpenAIChatModel.")

        # Import lazily
        try:
            from openai import OpenAI  # type: ignore
        except Exception as e:  # pragma: no cover - import path
            raise RuntimeError(
                "openai package not installed. Add 'openai' to requirements and lock."
            ) from e

        self._client = OpenAI(api_key=self._api_key)  # type: ignore[attr-defined]

    def generate(
        self,
        messages: Iterable[ChatMessage],
        *,
        temperature: float = 0.7,
        max_output_tokens: int | None = None,
    ) -> str:
        # Import here to keep the package optional at import time
        from openai import OpenAI  # type: ignore  # noqa: F401

        payload = [{"role": m.role.value, "content": m.content} for m in messages]
        resp = self._client.chat.completions.create(
            model=self._model,
            messages=payload,
            temperature=temperature,
            max_tokens=max_output_tokens,
        )
        choice = resp.choices[0]
        return (choice.message.content or "").strip()
