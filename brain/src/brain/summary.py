"""Reads the training statistics written by the world and compares brain variants."""

import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Window:
    tick: int
    lives_ended: int
    mean_life_length: float  # 0.0 when no life ended in the window

    @property
    def life_length_sum(self) -> float:
        return self.lives_ended * self.mean_life_length


def read_windows(path: Path) -> list[Window]:
    with open(path, newline="", encoding="utf-8") as file:
        return [
            Window(
                tick=int(row["tick"]),
                lives_ended=int(row["lives_ended"]),
                mean_life_length=float(row["mean_life_length"]) if row["mean_life_length"] else 0.0,
            )
            for row in csv.DictReader(file)
        ]


def mean_life(windows: list[Window]) -> float:
    """Mean life length over every life that ended in the given windows."""
    lives = sum(window.lives_ended for window in windows)
    if lives == 0:
        raise ValueError("No life ended in the given windows")
    return sum(window.life_length_sum for window in windows) / lives


def trained_mean_life(windows: list[Window], last_windows: int) -> float:
    """How long the NPCs live at the end of training: the mean over the last windows."""
    return mean_life(windows[-last_windows:])


def first_tick_reaching(windows: list[Window], threshold: float, rolling_windows: int) -> int | None:
    """First tick at which the mean life length over the last `rolling_windows` windows reaches the threshold.

    A single window holds only a few lives, so the check uses a rolling mean.
    """
    for end in range(rolling_windows, len(windows) + 1):
        recent = windows[end - rolling_windows:end]
        if sum(window.lives_ended for window in recent) > 0 and mean_life(recent) >= threshold:
            return recent[-1].tick
    return None
