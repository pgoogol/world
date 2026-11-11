"""Builders shared by the brain tests."""

import random

from brain.config import FixedTuningConfig, SelfTuningConfig
from brain.proto import brain_pb2
from brain.tuning import FixedSchedule, SelfTuning

NEED_LEVELS = (50, 80)
EMPTY = brain_pb2.CELL_EMPTY
FOOD = brain_pb2.CELL_FOOD
WATER = brain_pb2.CELL_WATER
LAVA = brain_pb2.CELL_LAVA
WALL = brain_pb2.CELL_WALL


def observation(centre=EMPTY, hunger=0, thirst=0, energy=100, x=5, y=5, cells=None) -> brain_pb2.Observation:
    if cells is None:
        cells = [EMPTY] * 9
        cells[4] = centre
    return brain_pb2.Observation(surroundings=cells, hunger=hunger, thirst=thirst, energy=energy, x=x, y=y)


def view(obs=None, reward=0.0, died=False, truncated=False, final=None, npc_id=0, neighbours=()) -> brain_pb2.NpcView:
    result = brain_pb2.NpcView(
        npc_id=npc_id, observation=obs if obs is not None else observation(), reward=reward, died=died,
        truncated=truncated, neighbour_ids=list(neighbours),
    )
    if final is not None:
        result.final_observation.CopyFrom(final)
    return result


def fixed(alpha=0.5, epsilon_start=0.0, epsilon_end=0.0, decay_ticks=100) -> FixedSchedule:
    """By default a greedy brain (epsilon 0), so tests control which action is taken."""
    return FixedSchedule(FixedTuningConfig(alpha, epsilon_start, epsilon_end, decay_ticks))


def self_tuning(epsilon_min=0.05, alpha_min=0.1) -> SelfTuning:
    return SelfTuning(SelfTuningConfig(epsilon_min, alpha_min))


def rng(seed=1) -> random.Random:
    return random.Random(seed)
