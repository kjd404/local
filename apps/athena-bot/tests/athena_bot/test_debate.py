import asyncio
from typing import Any, Dict
import discord

from athena_bot.debate import DebateManager, DebateStatus


class FakeRole:
    def __init__(self, id_: int, name: str = "@everyone") -> None:
        self.id = id_
        self.name = name


class FakeMember:
    def __init__(self, id_: int, display_name: str) -> None:
        self.id = id_
        self.display_name = display_name
        self.mention = f"<@{id_}>"


class FakeGuild:
    def __init__(self) -> None:
        self.default_role = FakeRole(0)
        self.id = 1
        self._members: Dict[int, FakeMember] = {}
        self._channels: Dict[int, FakeTextChannel] = {}
        self._next_id = 100

    def add_member(self, m: FakeMember) -> None:
        self._members[m.id] = m

    def get_member(self, id_: int):
        return self._members.get(id_)

    @property
    def text_channels(self):
        return list(self._channels.values())

    async def create_text_channel(
        self, name: str, overwrites: Dict[Any, discord.PermissionOverwrite], reason: str
    ):
        cid = self._next_id
        self._next_id += 1
        ch = FakeTextChannel(cid, name, self, overwrites)
        self._channels[cid] = ch
        return ch


class FakeTextChannel:
    def __init__(
        self,
        id_: int,
        name: str,
        guild: FakeGuild,
        overwrites: Dict[Any, discord.PermissionOverwrite],
    ):
        self.id = id_
        self.name = name
        self.guild = guild
        self._messages = []
        self.topic = None
        # Track permissions: object -> {send_messages: bool, view_channel: bool}
        self._perms: Dict[Any, Dict[str, Any]] = {}
        for target, ow in overwrites.items():
            self._perms[target] = {
                "send_messages": getattr(ow, "send_messages", None),
                "view_channel": getattr(ow, "view_channel", None),
                "read_messages": getattr(ow, "read_messages", None),
            }

    async def send(self, content: str):
        self._messages.append(content)

    async def set_permissions(self, target: Any, **kwargs):
        self._perms.setdefault(target, {})
        self._perms[target].update(kwargs)

    # Minimal emulation of discord.TextChannel.permissions_for
    class _Perm:
        def __init__(self, can_send: Any) -> None:
            self.send_messages = can_send

    def permissions_for(self, member: Any):
        can = self._perms.get(member, {}).get("send_messages")
        return self._Perm(can)

    async def edit(self, **kwargs):
        if "topic" in kwargs:
            self.topic = kwargs["topic"]

    # Helpers for tests
    def can_send(self, target: Any) -> Any:
        return self._perms.get(target, {}).get("send_messages")

    def can_view(self, target: Any) -> Any:
        return self._perms.get(target, {}).get("view_channel")


def test_create_public_debate_permissions():
    async def run():
        guild = FakeGuild()
        alice = FakeMember(1, "Alice")  # challenger
        bob = FakeMember(2, "Bob")  # challenged
        guild.add_member(alice)
        guild.add_member(bob)

        mgr = DebateManager()
        channel = await mgr.create_and_start(
            guild, alice, bob, topic="Best language", private=False
        )

        assert mgr.is_debate_channel(channel.id)
        d = mgr.get(channel.id)
        assert d is not None and d.status is DebateStatus.ACTIVE
        assert d.current_turn_id == alice.id
        # Permissions: public read, only challenger can send initially
        assert channel.can_view(guild.default_role) is True
        assert channel.can_send(guild.default_role) is False
        assert channel.can_send(alice) is True
        assert channel.can_send(bob) is False
        # Topic includes debate metadata
        assert channel.topic and channel.topic.startswith("[athena-debate]")

    asyncio.run(run())


def test_create_private_debate_permissions():
    async def run():
        guild = FakeGuild()
        a = FakeMember(10, "Ann")
        b = FakeMember(11, "Ben")
        guild.add_member(a)
        guild.add_member(b)

        mgr = DebateManager()
        channel = await mgr.create_and_start(guild, a, b, topic="Privacy", private=True)

        # Private: @everyone cannot view
        assert channel.can_view(guild.default_role) is False
        # Participants can send/view, but initial turn only allows challenger to send
        assert channel.can_send(a) is True
        assert channel.can_send(b) is False

    asyncio.run(run())


def test_turn_flips_after_messages():
    async def run():
        guild = FakeGuild()
        c = FakeMember(21, "Cat")
        d = FakeMember(22, "Dog")
        guild.add_member(c)
        guild.add_member(d)
        mgr = DebateManager()
        channel = await mgr.create_and_start(guild, c, d, topic="Topic")

        # First message by challenger -> flips to challenged
        await mgr.flip_turn(channel, c)
        assert channel.can_send(c) is False
        assert channel.can_send(d) is True
        # System message is posted for next turn
        assert channel._messages[-1].startswith("Your turn, ")

        # Next message by challenged -> flips to challenger
        await mgr.flip_turn(channel, d)
        assert channel.can_send(c) is True
        assert channel.can_send(d) is False

    asyncio.run(run())


def test_restore_from_guild_reconstructs_state():
    async def run():
        guild = FakeGuild()
        a = FakeMember(100, "Alpha")
        b = FakeMember(101, "Beta")
        guild.add_member(a)
        guild.add_member(b)
        mgr = DebateManager()
        channel = await mgr.create_and_start(guild, a, b, topic="Restore")

        # Flip once so that it's Beta's turn
        await mgr.flip_turn(channel, a)

        # New manager should restore from channel topic and perms
        mgr2 = DebateManager()
        restored = await mgr2.restore_from_guild(guild)  # type: ignore[arg-type]
        assert restored == 1
        d = mgr2.get(channel.id)
        assert d is not None
        assert d.current_turn_id == b.id

    asyncio.run(run())


def test_end_debate_locks_channel():
    async def run():
        guild = FakeGuild()
        x = FakeMember(31, "X")
        y = FakeMember(32, "Y")
        guild.add_member(x)
        guild.add_member(y)
        mgr = DebateManager()
        channel = await mgr.create_and_start(guild, x, y, topic="Endgame")

        await mgr.end_debate(channel)
        d = mgr.get(channel.id)
        assert d is not None and d.status is DebateStatus.ENDED
        # Everyone locked for send
        assert channel.can_send(guild.default_role) is False
        assert channel.can_send(x) is False
        assert channel.can_send(y) is False

        # Further flips should be no-ops
        await mgr.flip_turn(channel, x)
        assert channel.can_send(x) is False
        assert channel.can_send(y) is False

    asyncio.run(run())


def test_end_to_end_debate_flow():
    async def run():
        guild = FakeGuild()
        c = FakeMember(201, "Charlie")
        d = FakeMember(202, "Delta")
        guild.add_member(c)
        guild.add_member(d)
        mgr = DebateManager()

        # Create public debate
        channel = await mgr.create_and_start(
            guild, c, d, topic="E2E Flow", private=False
        )
        # Initial perms
        assert channel.can_send(c) is True
        assert channel.can_send(d) is False

        # Message 1 by C -> D's turn
        await mgr.flip_turn(channel, c)
        assert channel.can_send(c) is False
        assert channel.can_send(d) is True
        assert channel._messages[-1].startswith("Your turn, ")
        assert f"<@{d.id}>" in channel._messages[-1]

        # Message 2 by D -> C's turn
        await mgr.flip_turn(channel, d)
        assert channel.can_send(c) is True
        assert channel.can_send(d) is False
        assert f"<@{c.id}>" in channel._messages[-1]

        # Message 3 by C -> D's turn again
        await mgr.flip_turn(channel, c)
        assert channel.can_send(c) is False
        assert channel.can_send(d) is True
        assert f"<@{d.id}>" in channel._messages[-1]

        # End the debate
        await mgr.end_debate(channel)
        assert channel.can_send(guild.default_role) is False
        assert channel.can_send(c) is False
        assert channel.can_send(d) is False
        # Topic should be marked ENDED
        assert channel.topic and "status=ENDED" in channel.topic

        # Restore with a fresh manager
        mgr2 = DebateManager()
        restored = await mgr2.restore_from_guild(guild)  # type: ignore[arg-type]
        assert restored == 1
        d2 = mgr2.get(channel.id)
        assert d2 is not None and d2.status is DebateStatus.ENDED

    asyncio.run(run())
