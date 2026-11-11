"""The discovery map: where an NPC has seen water and food.

Every NPC keeps its own map, built from what it sees. Neighbours merge their maps, so the
settlement's knowledge of the board spreads through contacts and outlives single NPCs.
"""

from brain.encoding import need_level
from brain.proto import brain_pb2

Position = tuple[int, int]

# Direction codes used in the Q-table key: 0..8 = (sign dx + 1) * 3 + (sign dy + 1), so 4 means "here".
NO_DIRECTION = 9  # the NPC does not need the resource or knows no place with it


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

    def directions(self, observation: brain_pb2.Observation, levels: tuple[int, int], tick: int) -> bytes:
        """Two bytes for the Q-table key: direction to water and to food.

        An NPC thinks about a resource only when it needs it: with a low need the direction is
        NO_DIRECTION, which keeps the number of situations small.
        """
        x, y = observation.x, observation.y
        water = NO_DIRECTION
        if need_level(observation.thirst, levels) > 0:
            water = _direction_to_nearest(x, y, self.water)
        food = NO_DIRECTION
        if need_level(observation.hunger, levels) > 0:
            food = _direction_to_nearest(x, y, self._food_worth_visiting(tick))
        return bytes((water, food))

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


def _direction_to_nearest(x: int, y: int, places) -> int:
    best = None
    for place in places:
        candidate = (abs(place[0] - x) + abs(place[1] - y), place)
        if best is None or candidate < best:
            best = candidate
    if best is None:
        return NO_DIRECTION
    dx, dy = best[1][0] - x, best[1][1] - y
    return (_sign(dx) + 1) * 3 + (_sign(dy) + 1)


def _sign(value: int) -> int:
    return (value > 0) - (value < 0)
