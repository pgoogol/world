"""Brains: one instance decides for one NPC.

Every brain has the same interface:
  act(view) -> int   action index 0..5 for the NPC described by a proto NpcView
  start_run()        the world started a new run; forget any unfinished transition
"""

import random

from brain.proto import brain_pb2

# Action index -> contract enum. Index order is the order of the Q values.
ACTIONS = (
    brain_pb2.ACTION_STAY,
    brain_pb2.ACTION_UP,
    brain_pb2.ACTION_DOWN,
    brain_pb2.ACTION_LEFT,
    brain_pb2.ACTION_RIGHT,
    brain_pb2.ACTION_CONSUME,
)
ACTION_COUNT = len(ACTIONS)


def npc_rng(seed: int, npc_id: int) -> random.Random:
    """Each NPC draws from its own stream, so results do not depend on the order of NPCs."""
    return random.Random(seed * 1_000_003 + npc_id)


class RandomBrain:
    """Baseline for comparisons: uniformly random actions, no learning."""

    def __init__(self, rng: random.Random):
        self._rng = rng

    def act(self, view) -> int:
        return self._rng.randrange(ACTION_COUNT)

    def start_run(self) -> None:
        pass
