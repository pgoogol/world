"""What a settlement network chooses from, and how a choice becomes one action of the contract.

Two sets:
  RawActions  the six moves of stages 1 and 2 plus "walk to a known place" (step 3.9)
  Skills      composite skills of stage 3 (step 3.10): the network picks a skill every tick and the
              skill turns into a single move, the way "walk to a known place" did in stage 2

A skill uses only what the NPC sees and what its place map remembers. It never decides *whether*
something is worth doing: that is what the network learns.
"""

import random

from brain.places import PlaceMap, Position, WINTER, move_towards, nearest
from brain.proto import brain_pb2 as pb

EXPLORE_TICKS = 40      # how long an NPC keeps one direction while exploring
EXPLORE_REACH = 20      # how far ahead it aims


class Member:
    """One NPC as the settlement's brain sees it."""

    def __init__(self, npc_id: int, rng: random.Random, place_map: PlaceMap, tick: int):
        self.npc_id = npc_id
        self.rng = rng
        self.map = place_map
        self.born_tick = tick
        self.season = 0
        self.last_energy = 100
        self.last_position: Position | None = None
        self.full_shelters: set[Position] = set()
        self.explore_target: Position | None = None
        self.explore_until = 0
        # Learning: finished steps of the current piece of experience and the step waiting for its reward.
        self.steps: list = []
        self.pending = None
        self.discount = 1.0
        # The skill the NPC is carrying out and the tick at which it chooses again.
        self.choice: int | None = None
        self.decide_at = 0


class Situation:
    """One NPC at one tick: its observation plus cached answers from its place map."""

    def __init__(self, member: Member, observation, tick: int):
        self.member = member
        self.obs = observation
        self.tick = tick
        size = observation.view_size or 3
        self.here = (observation.x, observation.y)
        self.cell = observation.surroundings[(size // 2) * size + size // 2]
        self.carried = observation.carried_food + observation.carried_wood
        self._near: dict[str, Position | None] = {}

    def near(self, kind: str) -> Position | None:
        """The nearest known place of a kind; None when the NPC knows none."""
        if kind not in self._near:
            self._near[kind] = nearest(self.obs.x, self.obs.y, self._places(kind))
        return self._near[kind]

    def _places(self, kind: str):
        place_map = self.member.map
        if kind == "water":
            return place_map.places(pb.CELL_WATER)
        if kind == "food":
            return place_map.food_places(self.tick, self.obs.season)
        if kind == "tree":
            return place_map.places(pb.CELL_TREE)
        if kind == "store":
            return place_map.places(pb.CELL_STORE)
        if kind == "stocked":
            return [p for p in place_map.places(pb.CELL_STORE) if place_map.amount(p) > 0]
        if kind == "shelter":
            full = self.member.full_shelters
            return [p for p in place_map.places(pb.CELL_SHELTER) if p not in full]
        if kind == "site_shelter":
            return place_map.places(pb.CELL_SITE_SHELTER)
        if kind == "site_store":
            return place_map.places(pb.CELL_SITE_STORE)
        if kind == "site":
            return place_map.places(pb.CELL_SITE_SHELTER, pb.CELL_SITE_STORE)
        raise ValueError(f"Unknown kind of place: {kind!r}")

    def go(self, target: Position) -> int:
        return move_towards(self.obs, target)


class RawActions:
    """Step 3.9: the choices of the stage 2 brain, so the network is checked on a task we can measure."""

    MOVES = (pb.ACTION_STAY, pb.ACTION_UP, pb.ACTION_DOWN, pb.ACTION_LEFT, pb.ACTION_RIGHT, pb.ACTION_CONSUME)
    names = ("stay", "up", "down", "left", "right", "consume", "follow_map")
    count = len(names)

    def __init__(self, need_low: int):
        self._need_low = need_low

    def _target(self, s: Situation) -> Position | None:
        """The nearest known place of the resource the NPC needs more, as in stage 2."""
        thirsty = s.obs.thirst >= self._need_low
        hungry = s.obs.hunger >= self._need_low
        if thirsty and (not hungry or s.obs.thirst >= s.obs.hunger):
            return s.near("water")
        if hungry:
            return s.near("food")
        return None

    def mask(self, s: Situation) -> list[bool]:
        return [True] * 6 + [self._target(s) is not None]

    def action(self, choice: int, s: Situation) -> int:
        if choice < 6:
            return self.MOVES[choice]
        target = self._target(s)
        return pb.ACTION_CONSUME if target == s.here else s.go(target)


DRINK, EAT, GATHER_FOOD, GATHER_WOOD, STORE_FOOD, TAKE_FOOD, PLANT, BUILD_SHELTER, BUILD_STORE, TAKE_SHELTER, EXPLORE \
    = range(11)


class Skills:
    """Step 3.10: composite skills. Every skill is one line of intent and at most one walk."""

    names = ("drink", "eat", "gather_food", "gather_wood", "store_food", "take_food", "plant",
             "build_shelter", "build_store", "take_shelter", "explore")
    count = len(names)

    def __init__(self, inventory_size: int, board: tuple[int, int] = (64, 64), enabled: frozenset[int] | None = None):
        self._inventory = inventory_size
        self._board = board
        # The control settlement of the stage: the same network without planting, storing and building.
        self._enabled = enabled if enabled is not None else frozenset(range(self.count))

    def mask(self, s: Situation) -> list[bool]:
        obs, cell = s.obs, s.cell
        room = s.carried < self._inventory
        on_food = cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE)
        mask = [
            cell == pb.CELL_WATER or s.near("water") is not None,
            obs.carried_food > 0 or on_food or s.near("food") is not None or s.near("stocked") is not None
            or (cell == pb.CELL_STORE and obs.here_amount > 0),
            room and (on_food or s.near("food") is not None),
            room and s.near("tree") is not None,
            obs.carried_food > 0 and s.near("store") is not None,
            room and s.near("stocked") is not None,
            obs.carried_food > 0 and cell == pb.CELL_EMPTY and s.here not in s.member.map.food_spots,
            s.near("site_shelter") is not None or cell == pb.CELL_EMPTY,
            s.near("site_store") is not None or cell == pb.CELL_EMPTY,
            s.near("shelter") is not None,
            True,
        ]
        return [allowed and index in self._enabled for index, allowed in enumerate(mask)]

    def action(self, choice: int, s: Situation) -> int:
        obs, cell = s.obs, s.cell
        if choice == DRINK:
            return pb.ACTION_CONSUME if cell == pb.CELL_WATER else s.go(s.near("water"))
        if choice == EAT:
            if cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE) or (cell == pb.CELL_STORE and obs.here_amount > 0):
                return pb.ACTION_CONSUME
            if obs.carried_food > 0:
                return pb.ACTION_EAT_CARRIED
            # Food the NPC is sure of comes first: a stocked store in winter, the nearer of the two otherwise.
            targets = [t for t in (s.near("food"), s.near("stocked")) if t is not None]
            return s.go(nearest(obs.x, obs.y, targets))
        if choice == GATHER_FOOD:
            return pb.ACTION_GATHER if cell in (pb.CELL_FOOD, pb.CELL_FIELD_RIPE) else s.go(s.near("food"))
        if choice == GATHER_WOOD:
            return pb.ACTION_GATHER if cell == pb.CELL_TREE else s.go(s.near("tree"))
        if choice == STORE_FOOD:
            return pb.ACTION_DEPOSIT if cell == pb.CELL_STORE else s.go(s.near("store"))
        if choice == TAKE_FOOD:
            if cell == pb.CELL_STORE and obs.here_amount > 0:
                return pb.ACTION_WITHDRAW
            return s.go(s.near("stocked"))
        if choice == PLANT:
            return pb.ACTION_PLANT
        if choice == BUILD_SHELTER:
            return self._build(s, "site_shelter", pb.CELL_SITE_SHELTER, pb.ACTION_BUILD_SHELTER)
        if choice == BUILD_STORE:
            return self._build(s, "site_store", pb.CELL_SITE_STORE, pb.ACTION_BUILD_STORE)
        if choice == TAKE_SHELTER:
            return s.go(s.near("shelter"))
        return self._explore(s)

    @staticmethod
    def _build(s: Situation, kind: str, site_cell: int, action: int) -> int:
        """Join the nearest open site of this kind; only when the NPC knows none, start one here."""
        if s.cell == site_cell:
            return action
        site = s.near(kind)
        if site is not None:
            return s.go(site)
        return action

    def _explore(self, s: Situation) -> int:
        """Walk towards a far point for a while, then pick another: cheap and it covers the board."""
        member = s.member
        if member.explore_target is None or s.tick >= member.explore_until or member.explore_target == s.here:
            width, height = self._board
            x = min(width - 2, max(1, s.obs.x + member.rng.randint(-EXPLORE_REACH, EXPLORE_REACH)))
            y = min(height - 2, max(1, s.obs.y + member.rng.randint(-EXPLORE_REACH, EXPLORE_REACH)))
            member.explore_target = (x, y)
            member.explore_until = s.tick + EXPLORE_TICKS
        return s.go(member.explore_target)


def note_full_shelter(member: Member, s: Situation, need_high: int) -> None:
    """A shelter that does not keep the NPC warm is full: in winter energy drops there although neither
    hunger nor thirst is high. The NPC remembers it until spring and looks for another one."""
    obs = s.obs
    if obs.season != WINTER:
        member.full_shelters.clear()
    elif s.cell == pb.CELL_SHELTER and member.last_position == s.here and obs.energy < member.last_energy \
            and obs.hunger < need_high and obs.thirst < need_high:
        member.full_shelters.add(s.here)
    member.last_energy = obs.energy
    member.last_position = s.here
