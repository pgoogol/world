"""Turns a raw observation from the world into a Q-table key."""

from brain.proto import brain_pb2

SURROUNDINGS_SIZE = 9


def need_level(value: int, levels: tuple[int, int]) -> int:
    """0 = low, 1 = medium, 2 = high. `levels` holds the lower bounds of medium and high."""
    if value < levels[0]:
        return 0
    if value < levels[1]:
        return 1
    return 2


def encode(observation: brain_pb2.Observation, levels: tuple[int, int]) -> bytes:
    """Key: the 9 cell types around the NPC, then the hunger level and the thirst level."""
    cells = observation.surroundings
    if len(cells) != SURROUNDINGS_SIZE:
        raise ValueError(f"Expected {SURROUNDINGS_SIZE} surrounding cells, got {len(cells)}")
    if brain_pb2.CELL_UNSPECIFIED in cells:
        raise ValueError("Observation contains an unspecified cell")
    return bytes((*cells, need_level(observation.hunger, levels), need_level(observation.thirst, levels)))
