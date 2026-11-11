"""Brains: one instance decides for one NPC.

Every brain has the same interface:
  act(view, tick) -> int   action index 0..5 for the NPC described by a proto NpcView
  start_run()              the world started a new run; forget any unfinished transition
"""

import random

from brain.discovery import DiscoveryMap
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

# A finished step of experience: (state, action, reward, next state or None when the step ended in a collapse).
Transition = tuple[bytes, int, float, bytes | None]


def npc_rng(seed: int, npc_id: int) -> random.Random:
    """Each NPC draws from its own stream, so results do not depend on the order of NPCs."""
    return random.Random(seed * 1_000_003 + npc_id)


class RandomBrain:
    """Baseline for comparisons: uniformly random actions, no learning."""

    def __init__(self, rng: random.Random):
        self._rng = rng

    def act(self, view, tick: int = 0) -> int:
        return self._rng.randrange(ACTION_COUNT)

    def start_run(self) -> None:
        pass


class QLearningBrain:
    """Tabular Q-learning with epsilon-greedy action choice.

    The table is a dict: encoded observation -> six action values. Next to it the brain counts
    how often it has been in each situation and how often it has tried each action there; the
    tuning variant turns those counters into epsilon and alpha.

    With a discovery map the key also holds the direction to the nearest known water and food.
    """

    def __init__(
        self,
        rng: random.Random,
        gamma: float,
        need_levels: tuple[int, int],
        tuning,
        learning: bool = True,
        discovery: DiscoveryMap | None = None,
    ):
        self._rng = rng
        self._gamma = gamma
        self._need_levels = need_levels
        self._tuning = tuning
        self._learning = learning  # False in show mode: no updates, minimal epsilon
        self._previous: tuple[bytes, int] | None = None
        self.discovery = discovery
        self.q: dict[bytes, list[float]] = {}
        self.state_visits: dict[bytes, int] = {}
        self.pair_tries: dict[bytes, list[int]] = {}
        self.steps = 0
        # The transition finished by the latest act(); neighbours may learn from it.
        self.last_transition: Transition | None = None

    def start_run(self) -> None:
        self._previous = None
        self.last_transition = None

    def act(self, view, tick: int = 0) -> int:
        if self.discovery is not None:
            self.discovery.see(view.observation, tick)
        state = self._key(view.observation, tick)

        self.last_transition = None
        if self._previous is not None:
            self.last_transition = self._finish_transition(view, state, tick)
            if self._learning:
                self._update(self.last_transition, weight=1.0)

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

    def learn_from(self, transition: Transition, weight: float) -> None:
        """Learns from a step a neighbour has just made, with a reduced learning rate."""
        if self._learning:
            self._update(transition, weight)

    def inherit(self, elders: list["QLearningBrain"], trust: float) -> None:
        """A newborn's start: the elders' values averaged by how often each elder tried the action.

        Counters come multiplied by `trust`, so second-hand knowledge still gets checked.
        """
        states = set()
        for elder in elders:
            states.update(elder.pair_tries)
        for state in states:
            values = [0.0] * ACTION_COUNT
            tries = [0] * ACTION_COUNT
            for action in range(ACTION_COUNT):
                total = 0
                weighted = 0.0
                knowers = 0
                for elder in elders:
                    elder_tries = elder.pair_tries.get(state)
                    if elder_tries is not None and elder_tries[action] > 0:
                        total += elder_tries[action]
                        weighted += elder_tries[action] * elder.q[state][action]
                        knowers += 1
                if total > 0:
                    values[action] = weighted / total
                    tries[action] = int(trust * total / knowers)
            self.q[state] = values
            self.pair_tries[state] = tries
            visits = [elder.state_visits[state] for elder in elders if state in elder.state_visits]
            if visits:
                self.state_visits[state] = int(trust * sum(visits) / len(visits))

        if self.discovery is not None:
            for elder in elders:
                if elder.discovery is not None:
                    self.discovery.merge(elder.discovery)

    def _key(self, observation, tick: int) -> bytes:
        key = encode(observation, self._need_levels)
        if self.discovery is None:
            return key
        return key + self.discovery.directions(observation, self._need_levels, tick)

    def _finish_transition(self, view, state: bytes, tick: int) -> Transition:
        previous_state, previous_action = self._previous
        if view.died:
            # A collapse is terminal: nothing follows it.
            return (previous_state, previous_action, view.reward, None)
        if view.truncated:
            # The life limit only cuts the episode: bootstrap from the state before the reset.
            if not view.HasField("final_observation"):
                raise ValueError(f"NPC {view.npc_id}: truncated view without final_observation")
            return (previous_state, previous_action, view.reward, self._key(view.final_observation, tick))
        return (previous_state, previous_action, view.reward, state)

    def _update(self, transition: Transition, weight: float) -> None:
        state, action, reward, next_state = transition
        target = reward if next_state is None else reward + self._gamma * self._best_value(next_state)

        tries = self.pair_tries.setdefault(state, [0] * ACTION_COUNT)
        tries[action] += 1
        alpha = weight * self._tuning.alpha(tries[action])
        values = self.q.setdefault(state, [0.0] * ACTION_COUNT)
        values[action] += alpha * (target - values[action])

    def _best_value(self, state: bytes) -> float:
        values = self.q.get(state)
        return max(values) if values is not None else 0.0

    def export_state(self) -> dict:
        return {
            "q": self.q,
            "state_visits": self.state_visits,
            "pair_tries": self.pair_tries,
            "steps": self.steps,
            "discovery": self.discovery.export_state() if self.discovery is not None else None,
        }

    def import_state(self, state: dict) -> None:
        self.q = state["q"]
        self.state_visits = state["state_visits"]
        self.pair_tries = state["pair_tries"]
        self.steps = state["steps"]
        if self.discovery is not None and state.get("discovery") is not None:
            self.discovery.import_state(state["discovery"])
