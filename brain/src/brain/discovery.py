"""The discovery map: where an NPC has seen water and food.

Every NPC keeps its own map, built from what it sees. Neighbours merge their maps, so the
settlement's knowledge of the board spreads through contacts and outlives single NPCs.

The map gives the NPC one extra skill: walking towards a place it knows. Whether and when to
use that skill is still learned by Q-learning.
"""

from brain.encoding import need_level
from brain.proto import brain_pb2

Position = tuple[int, int]

# Action indices as used by the brains (see brain.brains.ACTIONS).
STAY = 0
CONSUME = 5
# (action index, dx, dy): up, down, left, right.
MOVES = ((1, 0, -1), (2, 0, 1), (3, -1, 0), (4, 1, 0))
BLOCKING = (brain_pb2.CELL_WALL, brain_pb2.CELL_LAVA)


class DiscoveryMap:
    def __init__(self, food_retry_ticks: int):
        self._food_retry_ticks = food_retry_ticks
        self.water: set[Position] = set()
        # Food place -> (tick it was last seen, whether food was there). Food grows back in the same
        # place, so a place seen empty is worth another visit after a while.
        self.food: dict[Position, tuple[int, bool]] = {}

    def see(self, observation: brain_pb2.Observation, tick: int) -> None:
        """Records the nine cells around the NPC."""
        x, y = observation.x, observation.y
        for index, cell in enumerate(observation.surroundings):
            position = (x + index % 3 - 1, y + index // 3 - 1)
            if cell == brain_pb2.CELL_WATER:
                self.water.add(position)
            elif cell == brain_pb2.CELL_FOOD:
                self.food[position] = (tick, True)
            elif position in self.food:
                self.food[position] = (tick, False)

    def merge(self, other: "DiscoveryMap") -> None:
        """Takes everything the other map knows; for a food place the more recent sighting wins."""
        self.water |= other.water
        for position, sighting in other.food.items():
            mine = self.food.get(position)
            if mine is None or sighting[0] > mine[0]:
                self.food[position] = sighting

    def target(self, observation: brain_pb2.Observation, levels: tuple[int, int], tick: int) -> Position | None:
        """The nearest known place of the resource the NPC needs more right now.

        None when both needs are low (the NPC is not looking for anything) or when the NPC
        knows no place with that resource.
        """
        thirsty = need_level(observation.thirst, levels) > 0
        hungry = need_level(observation.hunger, levels) > 0
        if thirsty and (not hungry or observation.thirst >= observation.hunger):
            return _nearest(observation.x, observation.y, self.water)
        if hungry:
            return _nearest(observation.x, observation.y, self._food_worth_visiting(tick))
        return None

    def _food_worth_visiting(self, tick: int) -> list[Position]:
        return [
            position
            for position, (seen, present) in self.food.items()
            if present or tick - seen >= self._food_retry_ticks
        ]

    def export_state(self) -> dict:
        return {"water": self.water, "food": self.food}

    def import_state(self, state: dict) -> None:
        self.water = state["water"]
        self.food = state["food"]


def step_towards(observation: brain_pb2.Observation, target: Position) -> int:
    """The action that brings the NPC closer to the target: eat or drink once it stands on it.

    The NPC looks where it steps: it never walks into a wall or onto lava it can see. When the
    direct way is blocked it takes the safe step that leaves it nearest to the target.
    """
    x, y = observation.x, observation.y
    if target == (x, y):
        return CONSUME
    best = None
    for action, dx, dy in MOVES:
        if observation.surroundings[(dy + 1) * 3 + (dx + 1)] in BLOCKING:
            continue
        gap_x, gap_y = target[0] - (x + dx), target[1] - (y + dy)
        # Steps first, then the straight-line distance: with the way ahead blocked, a step
        # sideways (which opens a way around) beats a step back.
        candidate = (abs(gap_x) + abs(gap_y), gap_x * gap_x + gap_y * gap_y, action)
        if best is None or candidate < best:
            best = candidate
    return best[2] if best is not None else STAY


def _nearest(x: int, y: int, places) -> Position | None:
    best = None
    for place in places:
        candidate = (abs(place[0] - x) + abs(place[1] - y), place)
        if best is None or candidate < best:
            best = candidate
    return best[1] if best is not None else None
