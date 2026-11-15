"""Step 3.10: a settlement that follows a hand-written scenario made of composite skills only.

It answers one question before anything learns: are the skills enough to get a settlement through
a winter? Every NPC decides for itself, from its own observation and its own place map; the only
thing it may do is pick one of the skills the network will pick from. Unlike the scenario of step
3.6 there is no pooled memory and no plan for the group.
"""

from brain.brains import npc_rng
from brain.places import PlaceMap, WINTER
from brain.proto import brain_pb2 as pb
from brain.scripted import ScenarioConfig
from brain.skills import (
    BUILD_SHELTER,
    BUILD_STORE,
    DRINK,
    EAT,
    EXPLORE,
    GATHER_FOOD,
    GATHER_WOOD,
    PLANT,
    STORE_FOOD,
    TAKE_FOOD,
    TAKE_SHELTER,
    Member,
    Situation,
    Skills,
    note_full_shelter,
)

SPRING, SUMMER, AUTUMN = 1, 2, 3
NEED = 50               # drink and eat from this level
HEAD_HOME_TICKS = 80    # in autumn, this many ticks before winter everyone heads for a shelter
HOME_REACH = 3          # buildings and fields lie this close to home
WINTER_RATION = 3       # portions an NPC takes to its shelter
BUILDER_EVERY = 3       # every third NPC builds, the others gather
SHELTERS_WANTED = 8     # enough places for a settlement that grows a little


class SkillScriptSettlement:
    def __init__(self, scenario: ScenarioConfig, seed: int, inventory_size: int = 4,
                 board: tuple[int, int] = (64, 64), food_retry_ticks: int = 60):
        self._scenario = scenario
        self._seed = seed
        self._inventory = inventory_size
        self._food_retry_ticks = food_retry_ticks
        self.skills = Skills(inventory_size, board)
        self.members: dict[int, Member] = {}
        self.choice_counts = [0] * self.skills.count

    def step(self, request: pb.StepRequest) -> pb.StepResponse:
        if request.tick == 0:
            self.members.clear()
        for npc_id in request.retired_npc_ids:
            self.members.pop(npc_id, None)

        situations = []
        for view in request.npcs:
            member = self.members.get(view.npc_id)
            if member is None:
                member = self.members[view.npc_id] = Member(
                    view.npc_id, npc_rng(self._seed, view.npc_id), PlaceMap(self._food_retry_ticks), request.tick)
            observation = view.observation
            if observation.season == WINTER and member.season != WINTER:
                member.map.winter_rot(request.tick)
            member.season = observation.season
            member.map.see(observation, request.tick)
            situations.append(Situation(member, observation, request.tick))
        for view in request.npcs:
            mine = self.members[view.npc_id]
            for neighbour_id in view.neighbour_ids:
                theirs = self.members.get(neighbour_id)
                if theirs is not None and view.npc_id < neighbour_id:
                    mine.map.merge(theirs.map, neighbour_id)
                    theirs.map.merge(mine.map, view.npc_id)

        response = pb.StepResponse()
        for view, situation in zip(request.npcs, situations):
            note_full_shelter(situation.member, situation, need_high=80)
            mask = self.skills.mask(situation)
            skill = self.choose(situation, mask)
            if not mask[skill]:
                skill = EXPLORE
            self.choice_counts[skill] += 1
            response.actions.add(npc_id=view.npc_id, action=self.skills.action(skill, situation))
        return response

    def save(self) -> None:
        """The scenario keeps nothing worth saving between runs."""

    # --- the scenario: which skill now ----------------------------------------------------------

    def choose(self, s: Situation, mask: list[bool]) -> int:
        obs = s.obs
        if obs.thirst >= NEED and mask[DRINK]:
            return DRINK
        if obs.hunger >= NEED and mask[EAT]:
            return EAT
        winter_coming = obs.season == AUTUMN and obs.season_ticks_left <= HEAD_HOME_TICKS
        if obs.season == WINTER or winter_coming:
            return self._winter(s, mask)
        if not self.scenario_builds():
            return GATHER_FOOD if mask[GATHER_FOOD] else EXPLORE
        if s.member.npc_id % BUILDER_EVERY == 0 or obs.carried_wood > 0:
            skill = self._build(s, mask)
            if skill is not None:
                return skill
        return self._gather(s, mask)

    def scenario_builds(self) -> bool:
        return self._scenario.build

    def _winter(self, s: Situation, mask: list[bool]) -> int:
        """Sit in a shelter with a few portions at hand: every walk to a store goes through the cold."""
        obs = s.obs
        if obs.carried_food < WINTER_RATION and mask[TAKE_FOOD] and (s.cell == pb.CELL_STORE or obs.carried_food == 0):
            return TAKE_FOOD
        if mask[TAKE_SHELTER]:
            return TAKE_SHELTER
        return DRINK if mask[DRINK] and s.cell != pb.CELL_WATER else EXPLORE

    def _build(self, s: Situation, mask: list[bool]) -> int | None:
        """A store first (without one there is nowhere to put the harvest), then shelters."""
        place_map = s.member.map
        if not place_map.places(pb.CELL_STORE) or place_map.places(pb.CELL_SITE_STORE):
            skill, kind, wood_needed = BUILD_STORE, "site_store", self._scenario.store_wood
        elif len(place_map.places(pb.CELL_SHELTER)) < SHELTERS_WANTED or place_map.places(pb.CELL_SITE_SHELTER):
            skill, kind, wood_needed = BUILD_SHELTER, "site_shelter", self._scenario.shelter_wood
        else:
            return None
        obs = s.obs
        site = s.near(kind)
        if site is not None:
            if place_map.amount(site) >= wood_needed or obs.carried_wood > 0:
                return skill
            if s.carried >= self._inventory:
                return None             # hands full of food: the gathering rules take it to a store
            return GATHER_WOOD if mask[GATHER_WOOD] else EXPLORE
        # No open site: bring a full load of wood home and start one there. Home is the nearest store
        # (newborns appear at stores and must find a shelter at once) or, before the first store, water.
        if obs.carried_wood >= min(self._inventory, wood_needed) or (obs.carried_wood > 0
                                                                     and s.carried >= self._inventory):
            store = s.near("store")
            home = store if store is not None else s.near("water")
            if home is None:
                return EXPLORE
            distance = abs(home[0] - obs.x) + abs(home[1] - obs.y)
            if distance > HOME_REACH:
                # The skills that walk towards a store or towards water.
                if store is None:
                    return DRINK
                return STORE_FOOD if mask[STORE_FOOD] else TAKE_FOOD if mask[TAKE_FOOD] else EXPLORE
            if s.cell == pb.CELL_EMPTY and s.here not in place_map.food_spots:
                return skill
            return EXPLORE
        if s.carried >= self._inventory:
            return None
        return GATHER_WOOD if mask[GATHER_WOOD] else EXPLORE

    def _gather(self, s: Situation, mask: list[bool]) -> int:
        obs = s.obs
        store = s.near("store")
        near_home = store is not None and abs(store[0] - obs.x) + abs(store[1] - obs.y) <= HOME_REACH
        if obs.season == SPRING and obs.carried_food >= 2 and near_home and mask[PLANT]:
            return PLANT
        if s.carried >= self._inventory or (obs.carried_food > 0 and obs.carried_wood > 0):
            if mask[STORE_FOOD]:
                return STORE_FOOD
        if mask[GATHER_FOOD]:
            return GATHER_FOOD
        if obs.carried_food > 0 and mask[STORE_FOOD]:
            return STORE_FOOD
        return EXPLORE
