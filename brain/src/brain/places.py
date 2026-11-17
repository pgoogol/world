"""The place map of stage 3: what an NPC knows about the board, from its own eyes and from contacts.

It generalises the discovery map of stage 2 to any view size and to every cell type. Each NPC keeps
its own map. Neighbours swap what is new since their last contact, so knowledge spreads through the
settlement and outlives single NPCs.
"""

from collections import deque

from brain.proto import brain_pb2 as pb

Position = tuple[int, int]

MOVES = ((pb.ACTION_UP, 0, -1), (pb.ACTION_DOWN, 0, 1), (pb.ACTION_LEFT, -1, 0), (pb.ACTION_RIGHT, 1, 0))
BLOCKING = frozenset((pb.CELL_WALL, pb.CELL_LAVA))
WINTER = 4
# Cells whose amount the world reports while the NPC stands on them.
_COUNTED = frozenset((pb.CELL_STORE, pb.CELL_SITE_SHELTER, pb.CELL_SITE_STORE, pb.CELL_FIELD_RIPE))
# The amount of a counted cell nobody has stood on yet: a store seen from the side may hold food.
UNKNOWN = -1


class PlaceMap:
    def __init__(self, food_retry_ticks: int):
        self._food_retry_ticks = food_retry_ticks
        # position -> (cell type, tick of the sighting that set it, amount last seen there)
        self.cells: dict[Position, tuple[int, int, int]] = {}
        self.by_type: dict[int, set[Position]] = {}
        # Places where wild food has been seen: it grows back in the same place.
        self.food_spots: set[Position] = set()
        # Every change in order, so that a neighbour takes only what is new since the last contact.
        self.log: list[tuple[Position, int, int, int]] = []
        self._taken: dict[int, int] = {}

    def see(self, observation, tick: int) -> None:
        """Records the square the NPC sees and the amount on the cell under it."""
        size = observation.view_size or 3
        radius = size // 2
        x0, y0 = observation.x - radius, observation.y - radius
        centre = radius * size + radius
        cells = self.cells
        for index, cell in enumerate(observation.surroundings):
            position = (x0 + index % size, y0 + index // size)
            known = cells.get(position)
            if index == centre and cell in _COUNTED:
                if known is None or known[0] != cell or known[2] != observation.here_amount:
                    self._set(position, cell, tick, observation.here_amount)
            elif known is None or known[0] != cell:
                self._set(position, cell, tick, UNKNOWN if cell in _COUNTED else 0)
            elif cell == pb.CELL_EMPTY and position in self.food_spots and tick - known[1] >= self._food_retry_ticks:
                # Still empty after the retry time: wait again before the next visit.
                self._set(position, cell, tick, 0)

    def merge(self, other: "PlaceMap", other_id: int) -> None:
        """Takes what the other map learned since the last contact; the more recent sighting wins."""
        start = self._taken.get(other_id, 0)
        log = other.log
        for index in range(start, len(log)):
            position, cell, tick, amount = log[index]
            known = self.cells.get(position)
            if known is None or tick > known[1]:
                if amount == UNKNOWN and known is not None and known[0] == cell:
                    continue            # a glance from the side says nothing new about what lies there
                self._set(position, cell, tick, amount)
        self._taken[other_id] = len(log)

    def winter_rot(self, tick: int) -> None:
        """First day of winter: wild food and ripe fields are gone, also where nobody is looking."""
        for cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE):
            for position in list(self.by_type.get(cell, ())):
                self._set(position, pb.CELL_EMPTY, tick, 0)

    def places(self, *types: int) -> list[Position]:
        result: list[Position] = []
        for cell in types:
            result.extend(self.by_type.get(cell, ()))
        return result

    def food_places(self, tick: int, season: int) -> list[Position]:
        """Wild food and ripe fields the NPC knows, plus food spots seen empty long enough ago."""
        result = self.places(pb.CELL_FOOD, pb.CELL_FIELD_RIPE)
        if season != WINTER:
            cells, retry = self.cells, self._food_retry_ticks
            for position in self.food_spots:
                cell, seen, _ = cells[position]
                if cell == pb.CELL_EMPTY and tick - seen >= retry:
                    result.append(position)
        return result

    def amount(self, position: Position) -> int:
        """What lies on a counted cell as last seen; UNKNOWN (-1) when nobody has stood on it."""
        known = self.cells.get(position)
        return known[2] if known is not None else 0

    def known_amount(self, position: Position) -> int:
        """The amount as a count: unknown counts as nothing."""
        return max(0, self.amount(position))

    def cell(self, position: Position) -> int | None:
        known = self.cells.get(position)
        return known[0] if known is not None else None

    def _set(self, position: Position, cell: int, tick: int, amount: int) -> None:
        known = self.cells.get(position)
        if known is not None and known[0] != cell:
            self.by_type[known[0]].discard(position)
        self.cells[position] = (cell, tick, amount)
        self.by_type.setdefault(cell, set()).add(position)
        if cell == pb.CELL_FOOD:
            self.food_spots.add(position)
        elif cell != pb.CELL_EMPTY:
            # Something was built or sown here: wild food will not grow back.
            self.food_spots.discard(position)
        self.log.append((position, cell, tick, amount))


def nearest(x: int, y: int, places) -> Position | None:
    best = None
    best_key = None
    for place in places:
        key = (abs(place[0] - x) + abs(place[1] - y), place)
        if best_key is None or key < best_key:
            best_key = key
            best = place
    return best


def move_towards(observation, target: Position) -> int:
    """The move that brings the NPC closer to the target; ACTION_STAY when it stands on it.

    The NPC looks where it steps: it never walks into a wall or onto lava it can see. When the
    direct way is blocked it finds a way around inside the square it sees.
    """
    x, y = observation.x, observation.y
    gap_x, gap_y = target[0] - x, target[1] - y
    if gap_x == 0 and gap_y == 0:
        return pb.ACTION_STAY
    size = observation.view_size or 3
    radius = size // 2
    cells = observation.surroundings
    centre = radius * size + radius

    horizontal = (pb.ACTION_RIGHT, 1) if gap_x > 0 else (pb.ACTION_LEFT, -1)
    vertical = (pb.ACTION_DOWN, size) if gap_y > 0 else (pb.ACTION_UP, -size)
    first, second = (horizontal, vertical) if abs(gap_x) >= abs(gap_y) else (vertical, horizontal)
    for (action, offset), gap in ((first, max(abs(gap_x), abs(gap_y))), (second, min(abs(gap_x), abs(gap_y)))):
        if gap > 0 and cells[centre + offset] not in BLOCKING:
            return action
    return _way_around(cells, size, radius, gap_x, gap_y)


def _way_around(cells, size: int, radius: int, gap_x: int, gap_y: int) -> int:
    """Breadth-first search inside the view: head for the visible cell nearest to the target."""
    start = (radius, radius)
    first_move: dict[tuple[int, int], int] = {start: pb.ACTION_STAY}
    queue = deque([(start, 0)])
    best = None
    while queue:
        (cx, cy), steps = queue.popleft()
        if steps > 0:
            left_x, left_y = gap_x - (cx - radius), gap_y - (cy - radius)
            key = (abs(left_x) + abs(left_y), steps, first_move[(cx, cy)])
            if best is None or key < best:
                best = key
        for action, dx, dy in MOVES:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < size and 0 <= ny < size and (nx, ny) not in first_move \
                    and cells[ny * size + nx] not in BLOCKING:
                first_move[(nx, ny)] = action if steps == 0 else first_move[(cx, cy)]
                queue.append(((nx, ny), steps + 1))
    return best[2] if best is not None else pb.ACTION_STAY
