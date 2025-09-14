import pytest

from athena_bot.config import AthenaConfig


def test_missing_token_exits(monkeypatch):
    monkeypatch.delenv("DISCORD_BOT_TOKEN", raising=False)
    with pytest.raises(SystemExit) as exc:
        _ = AthenaConfig.from_env()
    assert "DISCORD_BOT_TOKEN is required" in str(exc.value)


def test_present_token(monkeypatch):
    monkeypatch.setenv("DISCORD_BOT_TOKEN", "abc123")
    cfg = AthenaConfig.from_env()
    assert cfg.token == "abc123"
