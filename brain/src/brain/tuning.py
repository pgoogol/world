"""How an NPC sets its exploration rate (epsilon) and learning rate (alpha)."""

from brain.config import FixedTuningConfig


class FixedSchedule:
    """Control variant: epsilon falls linearly with the NPC's age in ticks, alpha is constant."""

    def __init__(self, config: FixedTuningConfig):
        self._config = config
        self.epsilon_floor = config.epsilon_end

    def epsilon(self, steps: int) -> float:
        config = self._config
        progress = min(1.0, steps / config.epsilon_decay_ticks)
        return config.epsilon_start + (config.epsilon_end - config.epsilon_start) * progress

    def alpha(self) -> float:
        return self._config.alpha
