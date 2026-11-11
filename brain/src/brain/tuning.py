"""How an NPC sets its exploration rate (epsilon) and learning rate (alpha).

Both variants share one interface:
  epsilon(steps, state_visits)   steps: ticks the NPC has lived in total,
                                 state_visits: earlier visits to the current situation
  alpha(pair_tries)              how many times the NPC has tried this action in this situation,
                                 counting the current try
  epsilon_floor                  exploration rate used in show mode
"""

import math

from brain.config import FixedTuningConfig, SelfTuningConfig


class FixedSchedule:
    """Control variant: epsilon falls linearly with the NPC's age in ticks, alpha is constant."""

    def __init__(self, config: FixedTuningConfig):
        self._config = config
        self.epsilon_floor = config.epsilon_end

    def epsilon(self, steps: int, state_visits: int = 0) -> float:
        config = self._config
        progress = min(1.0, steps / config.epsilon_decay_ticks)
        return config.epsilon_start + (config.epsilon_end - config.epsilon_start) * progress

    def alpha(self, pair_tries: int = 1) -> float:
        return self._config.alpha


class SelfTuning:
    """The NPC tunes itself from its own visit counters; nobody hands it a schedule.

    In a new situation it experiments (epsilon 1), in a familiar one it uses what it knows.
    The first tries of an action move its value a lot, later ones only smooth it.
    """

    def __init__(self, config: SelfTuningConfig):
        self._config = config
        self.epsilon_floor = config.epsilon_min

    def epsilon(self, steps: int, state_visits: int) -> float:
        return max(self._config.epsilon_min, 1.0 / math.sqrt(1 + state_visits))

    def alpha(self, pair_tries: int) -> float:
        return max(self._config.alpha_min, 1.0 / pair_tries)
