"""Brains: one instance decides for one NPC.

Every brain has the same interface:
  act(view) -> int   action index 0..5 for the NPC described by a proto NpcView
  start_run()        the world started a new run; forget any unfinished transition
"""

import random

from brain.encoding import encode
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


class QLearningBrain:
    """Tabular Q-learning with epsilon-greedy action choice.

    The table is a dict: encoded observation -> six action values. It grows only by
    situations the NPC has actually been in. Next to it the brain counts how often it has
    been in each situation and how often it has tried each action there; the tuning
    variant turns those counters into epsilon and alpha.
    """

    def __init__(self, rng: random.Random, gamma: float, need_levels: tuple[int, int], tuning, learning: bool = True):
        self._rng = rng
        self._gamma = gamma
        self._need_levels = need_levels
        self._tuning = tuning
        self._learning = learning  # False in show mode: no updates, minimal epsilon
        self._previous: tuple[bytes, int] | None = None
        self.q: dict[bytes, list[float]] = {}
        self.state_visits: dict[bytes, int] = {}
        self.pair_tries: dict[bytes, list[int]] = {}
        self.steps = 0

    def start_run(self) -> None:
        self._previous = None

    def act(self, view) -> int:
        state = encode(view.observation, self._need_levels)
        if self._learning and self._previous is not None:
            self._learn(view, state)

        values = self.q.setdefault(state, [0.0] * ACTION_COUNT)
        if self._learning:
            visits = self.state_visits.get(state, 0)
            epsilon = self._tuning.epsilon(self.steps, visits)
            self.state_visits[state] = visits + 1
        else:
            epsilon = self._tuning.epsilon_floor
        if self._rng.random() < epsilon:
            action = self._rng.randrange(ACTION_COUNT)
        else:
            best = max(values)
            # Ties are broken at random: with a fresh table every value is equal.
            action = self._rng.choice([index for index, value in enumerate(values) if value == best])

        self._previous = (state, action)
        self.steps += 1
        return action

    def _learn(self, view, state: bytes) -> None:
        previous_state, previous_action = self._previous
        if view.died:
            # Death is terminal: nothing follows it.
            target = view.reward
        elif view.truncated:
            # The life limit only cuts the episode: bootstrap from the state before the reset.
            if not view.HasField("final_observation"):
                raise ValueError(f"NPC {view.npc_id}: truncated view without final_observation")
            final_state = encode(view.final_observation, self._need_levels)
            target = view.reward + self._gamma * self._best_value(final_state)
        else:
            target = view.reward + self._gamma * self._best_value(state)

        tries = self.pair_tries.setdefault(previous_state, [0] * ACTION_COUNT)
        tries[previous_action] += 1
        alpha = self._tuning.alpha(tries[previous_action])
        values = self.q[previous_state]
        values[previous_action] += alpha * (target - values[previous_action])

    def _best_value(self, state: bytes) -> float:
        values = self.q.get(state)
        return max(values) if values is not None else 0.0

    def export_state(self) -> dict:
        return {
            "q": self.q,
            "state_visits": self.state_visits,
            "pair_tries": self.pair_tries,
            "steps": self.steps,
        }

    def import_state(self, state: dict) -> None:
        self.q = state["q"]
        self.state_visits = state["state_visits"]
        self.pair_tries = state["pair_tries"]
        self.steps = state["steps"]
