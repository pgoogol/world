"""Step 3.6: a settlement driven by a hand-written scenario.

It proves that the stage 3 world can be survived (and tunes its parameters) before anything learns.
The scenario is a brain like any other: it gets only what the NPCs see and answers with actions.
Unlike the learning brains it pools what all NPCs have seen into one settlement memory and plans
for the whole group: where home is, how many shelters and stores to build, who builds and who gathers.

With `build=False` it runs the same scenario without shelters, stores and fields.
"""

import math
from collections import deque
from dataclasses import dataclass

from brain.proto import brain_pb2 as pb

Position = tuple[int, int]

STAY, UP, DOWN, LEFT, RIGHT = pb.ACTION_STAY, pb.ACTION_UP, pb.ACTION_DOWN, pb.ACTION_LEFT, pb.ACTION_RIGHT
MOVES = ((UP, 0, -1), (DOWN, 0, 1), (LEFT, -1, 0), (RIGHT, 1, 0))
BLOCKING = frozenset((pb.CELL_WALL, pb.CELL_LAVA))
SPRING, SUMMER, AUTUMN, WINTER = 1, 2, 3, 4
INVENTORY = 4


@dataclass(frozen=True)
class ScenarioConfig:
    build: bool
    season_ticks: int
    consume_relief: int
    shelter_capacity: int
    store_capacity: int
    shelter_wood: int
    store_wood: int
    # Thresholds of the scenario itself.
    drink_at: int = 50
    eat_at: int = 50
    head_home_ticks: int = 60      # in autumn, this many ticks before winter everyone goes home
    home_radius: int = 6           # fields and building sites lie this close to home
    winter_stock_margin: float = 1.3
    wild_food_reach: int = 15      # a hungry NPC walks this far for wild food before it turns to a store
    food_spot_retry_ticks: int = 90  # a wild food place seen empty is worth a visit again after this long
    spare_shelters: int = 2        # shelters built beyond the need, to use up wood nobody can put down


class SettlementMemory:
    """Everything the settlement's NPCs have seen, pooled."""

    def __init__(self):
        self.cells: dict[Position, int] = {}
        self.store_food: dict[Position, int] = {}
        self.site_wood: dict[Position, int] = {}
        # Places where wild food grows: tick when last seen empty, None when last seen with food.
        self.food_spots: dict[Position, int | None] = {}
        # Per-tick caches: the memory does not change while the NPCs decide.
        self._fields: dict[tuple, dict[Position, int]] = {}
        self._positions: dict[tuple, list[Position]] = {}
        self._frontier: list[Position] | None = None

    def begin_tick(self) -> None:
        self._fields = {}
        self._positions = {}
        self._frontier = None

    def see(self, observation, tick: int) -> None:
        size = observation.view_size or 3
        radius = size // 2
        for index, cell in enumerate(observation.surroundings):
            position = (observation.x + index % size - radius, observation.y + index // size - radius)
            self.cells[position] = cell
            if cell == pb.CELL_FOOD:
                self.food_spots[position] = None
            elif position in self.food_spots and cell == pb.CELL_EMPTY:
                if self.food_spots[position] is None:
                    self.food_spots[position] = tick
        here = (observation.x, observation.y)
        cell = self.cells[here]
        if cell == pb.CELL_STORE:
            self.store_food[here] = observation.here_amount
        elif cell in (pb.CELL_SITE_SHELTER, pb.CELL_SITE_STORE):
            self.site_wood[here] = observation.here_amount

    def forget_wild_food(self, tick: int) -> None:
        """Winter rot: wild food and unharvested ripe fields are gone, wherever nobody is looking."""
        for position, cell in self.cells.items():
            if cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE):
                self.cells[position] = pb.CELL_EMPTY
                if position in self.food_spots:
                    self.food_spots[position] = tick

    def wild_targets(self, tick: int, retry_ticks: int) -> list[Position]:
        """Known wild food and ripe fields, plus food spots seen empty long enough ago to have grown back."""
        cached = self._positions.get(("wild-targets",))
        if cached is None:
            cached = list(self.positions_of(pb.CELL_FOOD, pb.CELL_FIELD_RIPE))
            cached += [p for p, emptied in self.food_spots.items()
                       if emptied is not None and tick - emptied >= retry_ticks and self.cells.get(p) == pb.CELL_EMPTY]
            self._positions[("wild-targets",)] = cached
        return cached

    def walkable(self, position: Position) -> bool:
        cell = self.cells.get(position)
        return cell is not None and cell not in BLOCKING

    def positions_of(self, *types: int) -> list[Position]:
        cached = self._positions.get(types)
        if cached is None:
            cached = self._positions[types] = [p for p, cell in self.cells.items() if cell in types]
        return cached

    def frontier(self) -> list[Position]:
        """Known walkable cells next to an unknown one."""
        if self._frontier is None:
            self._frontier = [
                (x, y) for (x, y), cell in self.cells.items()
                if cell not in BLOCKING and any((x + dx, y + dy) not in self.cells for _, dx, dy in MOVES)
            ]
        return self._frontier

    def distances(self, key: tuple, targets) -> dict[Position, int]:
        """Steps from every reachable known cell to the nearest target; computed once per tick and key.

        `targets` is a list or a function returning one, called only when the field is computed.
        """
        field = self._fields.get(key)
        if field is None:
            if callable(targets):
                targets = targets()
            field = {target: 0 for target in targets if self.walkable(target)}
            queue = deque(field)
            while queue:
                x, y = queue.popleft()
                step = field[(x, y)] + 1
                for _, dx, dy in MOVES:
                    neighbour = (x + dx, y + dy)
                    if neighbour not in field and self.walkable(neighbour):
                        field[neighbour] = step
                        queue.append(neighbour)
            self._fields[key] = field
        return field


def step_along(field: dict[Position, int], position: Position) -> int | None:
    """The move to a neighbour closer to the nearest target; None when there is none."""
    here = field.get(position)
    if here is None:
        return None
    best = None
    for action, dx, dy in MOVES:
        distance = field.get((position[0] + dx, position[1] + dy))
        if distance is not None and distance < here and (best is None or distance < best[0]):
            best = (distance, action)
    return best[1] if best is not None else None


class ScriptedSettlement:
    """Answers Brain.Step for a whole settlement following a fixed scenario."""

    def __init__(self, config: ScenarioConfig):
        self.config = config
        self.memory = SettlementMemory()
        self.home: Position | None = None
        self.planned_sites: dict[Position, int] = {}   # position -> CELL_SITE_SHELTER / CELL_SITE_STORE
        self.season = 0
        self.tick = 0
        # For diagnosis: (tick, npc, season, hunger, thirst, cell, food, wood) of every NPC that died.
        self.deaths: list[tuple] | None = None

    # --- Brain.Step -----------------------------------------------------------------------------

    def step(self, request: pb.StepRequest) -> pb.StepResponse:
        memory = self.memory
        memory.begin_tick()
        season = request.npcs[0].observation.season if request.npcs else 0
        if season == WINTER and self.season != WINTER:
            memory.forget_wild_food(request.tick)
        self.season = season
        self.tick = request.tick
        for view in request.npcs:
            if view.died and self.deaths is not None:
                obs = view.observation
                centre = obs.surroundings[len(obs.surroundings) // 2]
                self.deaths.append((request.tick, view.npc_id, obs.season, obs.hunger, obs.thirst, centre,
                                    obs.carried_food, obs.carried_wood))
            memory.see(view.observation, request.tick)
        if self.home is None:
            self.home = self._choose_home(request)

        plan = self._plan(request)
        response = pb.StepResponse()
        ranked = sorted(request.npcs, key=lambda view: view.npc_id)
        for rank, view in enumerate(ranked):
            response.actions.add(npc_id=view.npc_id, action=self._decide(view.observation, rank, plan))
        return response

    def save(self) -> None:
        """The scenario keeps nothing worth saving between runs."""

    # --- planning for the whole settlement ------------------------------------------------------

    def _choose_home(self, request) -> Position | None:
        """An empty cell near water, as close as possible to where the NPCs stand."""
        water = self.memory.positions_of(pb.CELL_WATER)
        if not water or not request.npcs:
            return None
        cx = sum(view.observation.x for view in request.npcs) / len(request.npcs)
        cy = sum(view.observation.y for view in request.npcs) / len(request.npcs)
        to_water = self.memory.distances(("water",), water)
        candidates = [p for p, cell in self.memory.cells.items() if cell == pb.CELL_EMPTY and p in to_water]
        if not candidates:
            return None
        return min(candidates, key=lambda p: (abs(p[0] - cx) + abs(p[1] - cy) + 3 * to_water[p], p))

    def _plan(self, request) -> dict:
        config = self.config
        population = len(request.npcs)
        shelters = len(self.memory.positions_of(pb.CELL_SHELTER))
        stores = self.memory.positions_of(pb.CELL_STORE)
        meals = config.season_ticks / max(1, config.consume_relief)
        stock_needed = population * meals * config.winter_stock_margin
        shelters_needed = math.ceil((population + 2) / config.shelter_capacity) if config.build else 0
        stores_needed = max(1, math.ceil(stock_needed / config.store_capacity)) if config.build else 0

        # Finished buildings leave the plan.
        for position in list(self.planned_sites):
            if self.memory.cells.get(position) in (pb.CELL_SHELTER, pb.CELL_STORE):
                del self.planned_sites[position]
        open_shelters = sum(1 for kind in self.planned_sites.values() if kind == pb.CELL_SITE_SHELTER)
        open_stores = sum(1 for kind in self.planned_sites.values() if kind == pb.CELL_SITE_STORE)
        if self.home is not None and config.build:
            # Stores first: without one there is nowhere to put the harvest.
            if len(stores) + open_stores < stores_needed and open_stores < 1:
                self._plan_site(pb.CELL_SITE_STORE)
            if shelters + open_shelters < shelters_needed and open_shelters < 1:
                self._plan_site(pb.CELL_SITE_SHELTER)
            # Wood cannot be put down: if someone carries it and no site is open, open one more shelter,
            # up to a few spare ones (newborns need places too).
            if not self.planned_sites and shelters < shelters_needed + config.spare_shelters                     and any(view.observation.carried_wood for view in request.npcs):
                self._plan_site(pb.CELL_SITE_SHELTER)

        sites = sorted(self.planned_sites)
        stored = sum(self.memory.store_food.get(p, 0) for p in stores)
        return {
            "builders": min(population // 3, 3 * len(sites)) if sites else 0,
            "sites": sites,
            "stored": stored,
            "stock_needed": stock_needed,
        }

    def _plan_site(self, kind: int) -> None:
        """The free empty cell nearest to home becomes the next building site."""
        field = self.memory.distances(("home",), [self.home])
        candidates = [p for p, distance in field.items()
                      if distance <= self.config.home_radius and self.memory.cells.get(p) == pb.CELL_EMPTY
                      and p not in self.planned_sites and p != self.home]
        if candidates:
            self.planned_sites[min(candidates, key=lambda p: (field[p], p))] = kind

    # --- one NPC ---------------------------------------------------------------------------------

    def _decide(self, obs, rank: int, plan: dict) -> int:
        config = self.config
        here = (obs.x, obs.y)
        cell = self.memory.cells.get(here)

        # Survival comes first: drink, then eat.
        if cell == pb.CELL_WATER and obs.thirst > 10:
            return pb.ACTION_CONSUME
        if obs.thirst >= config.drink_at:
            return self._go(("water",), lambda: self.memory.positions_of(pb.CELL_WATER), here)
        if obs.hunger >= config.eat_at:
            action = self._eat(obs, here, cell)
            if action is not None:
                return action

        winter_coming = obs.season == AUTUMN and obs.season_ticks_left <= config.head_home_ticks
        if obs.season == WINTER or winter_coming:
            return self._winter(obs, here, cell, rank)

        if config.build and rank < plan["builders"]:
            action = self._build(obs, here, cell, rank, plan)
            if action is not None:
                return action
        return self._gather(obs, here, cell, plan)

    def _eat(self, obs, here, cell) -> int | None:
        """Eat where food is; outside winter prefer wild food unless it is far and a store is not."""
        if cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE) or (cell == pb.CELL_STORE and obs.here_amount > 0):
            return pb.ACTION_CONSUME
        if obs.carried_food > 0:
            return pb.ACTION_EAT_CARRIED
        # A hungry NPC goes for food it is sure of: food it has seen, then a store; places where wild
        # food may have grown back come last.
        seen = [] if obs.season == WINTER else self.memory.positions_of(pb.CELL_FOOD, pb.CELL_FIELD_RIPE)
        stores = [p for p in self.memory.positions_of(pb.CELL_STORE) if self.memory.store_food.get(p, 1) > 0]
        if seen:
            distance = self.memory.distances(("seen-food",), seen).get(here)
            if distance is not None and (distance <= self.config.wild_food_reach or not stores):
                return self._go(("seen-food",), seen, here)
        if stores:
            return self._go(("stores-with-food",), stores, here)
        wild = self._wild()
        if wild:
            return self._go(("wild",), wild, here)
        return None

    def _winter(self, obs, here, cell, rank) -> int:
        """Stay in a shelter, one NPC per free place; keep a portion at hand."""
        if cell == pb.CELL_STORE:
            # A store takes food only; wood stays in the hands.
            if obs.carried_food > 2:
                return pb.ACTION_DEPOSIT
            if obs.carried_food < 1 and obs.here_amount > 0 and obs.carried_food + obs.carried_wood < INVENTORY:
                return pb.ACTION_WITHDRAW
        shelters = sorted(self.memory.positions_of(pb.CELL_SHELTER))
        if not shelters:
            return STAY
        place = shelters[min(rank // self.config.shelter_capacity, len(shelters) - 1)]
        if here == place:
            return STAY
        return self._go(("shelter", place), [place], here)

    def _build(self, obs, here, cell, rank, plan) -> int | None:
        site = plan["sites"][rank % len(plan["sites"])]
        kind = self.planned_sites[site]
        action = pb.ACTION_BUILD_SHELTER if kind == pb.CELL_SITE_SHELTER else pb.ACTION_BUILD_STORE
        wood_needed = self.config.shelter_wood if kind == pb.CELL_SITE_SHELTER else self.config.store_wood
        site_cell = self.memory.cells.get(site)
        site_has_wood = site_cell == kind and self.memory.site_wood.get(site, 0) >= wood_needed
        carried = obs.carried_food + obs.carried_wood

        if here == site and (site_cell == pb.CELL_EMPTY or site_has_wood or obs.carried_wood > 0):
            return action
        if site_has_wood or obs.carried_wood >= INVENTORY or (obs.carried_wood > 0 and carried >= INVENTORY):
            return self._go(("site", site), [site], here)
        if carried >= INVENTORY:
            return None   # hands full of food: the gathering rules take it to a store first
        if cell == pb.CELL_TREE:
            return pb.ACTION_GATHER
        still_needed = wood_needed - (self.memory.site_wood.get(site, 0) if site_cell == kind else 0)
        if obs.carried_wood >= min(INVENTORY, still_needed):
            return self._go(("site", site), [site], here)
        if self.memory.positions_of(pb.CELL_TREE):
            return self._go(("trees",), lambda: self.memory.positions_of(pb.CELL_TREE), here)
        return self._explore(here)

    def _gather(self, obs, here, cell, plan) -> int:
        build = self.config.build
        carried = obs.carried_food + obs.carried_wood
        stores = self.memory.positions_of(pb.CELL_STORE)
        if build and obs.carried_wood > 0 and plan["sites"]:
            # Wood in the hands of a gatherer goes to the nearest site.
            if here in self.planned_sites:
                kind = self.planned_sites[here]
                return pb.ACTION_BUILD_SHELTER if kind == pb.CELL_SITE_SHELTER else pb.ACTION_BUILD_STORE
            return self._go(("sites",), plan["sites"], here)
        if build and stores and obs.carried_food > 0 and (carried >= INVENTORY or obs.carried_wood > 0):
            if cell == pb.CELL_STORE:
                return pb.ACTION_DEPOSIT
            return self._go(("stores",), stores, here)
        if build and obs.season == SPRING and obs.carried_food >= 2 and cell == pb.CELL_EMPTY \
                and here not in self.planned_sites and self._near_home(here) \
                and plan["stored"] >= plan["stock_needed"] / 2:
            return pb.ACTION_PLANT
        if carried < INVENTORY:
            if cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE):
                return pb.ACTION_GATHER
            wild = self._wild()
            if wild:
                return self._go(("wild",), wild, here)
            return self._explore(here)
        if self.home is not None:
            return self._go(("home",), [self.home], here)
        return STAY

    # --- moving ----------------------------------------------------------------------------------

    def _wild(self) -> list[Position]:
        if self.season == WINTER:
            return []
        return self.memory.wild_targets(self.tick, self.config.food_spot_retry_ticks)

    def _near_home(self, position: Position) -> bool:
        return self.home is not None and \
            abs(position[0] - self.home[0]) + abs(position[1] - self.home[1]) <= self.config.home_radius

    def _go(self, key: tuple, targets, here: Position) -> int:
        field = self.memory.distances(key, targets)
        action = step_along(field, here)
        if action is not None:
            return action
        if field.get(here) == 0:
            return STAY
        return self._explore(here)

    def _explore(self, here: Position) -> int:
        field = self.memory.distances(("frontier",), self.memory.frontier)
        action = step_along(field, here)
        return action if action is not None else STAY
