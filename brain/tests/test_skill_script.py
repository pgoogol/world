"""Step 3.10: the scenario made of skills only picks the skill each situation calls for."""

import random

from brain.places import PlaceMap
from brain.proto import brain_pb2 as pb
from brain.scripted import ScenarioConfig
from brain.skill_script import SkillScriptSettlement
from brain.skills import (
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
)

E, F, W, T, S, H = (pb.CELL_EMPTY, pb.CELL_FOOD, pb.CELL_WATER, pb.CELL_TREE, pb.CELL_STORE, pb.CELL_SHELTER)
SPRING, SUMMER, AUTUMN, WINTER = 1, 2, 3, 4
SCENARIO = ScenarioConfig(build=True, season_ticks=500, consume_relief=40, shelter_capacity=4,
                          store_capacity=100, shelter_wood=6, store_wood=4)


def settlement(build=True):
    return SkillScriptSettlement(ScenarioConfig(**{**SCENARIO.__dict__, "build": build}), seed=42)


def choose(places=None, *, npc_id=1, build=True, season=SUMMER, ticks_left=400, hunger=0, thirst=0, food=0, wood=0,
           here=0):
    """The skill the scenario picks for an NPC with a 7x7 view; `places` maps (dx, dy) to a cell type."""
    cells = [E] * 49
    for (dx, dy), cell in (places or {}).items():
        cells[(dy + 3) * 7 + dx + 3] = cell
    obs = pb.Observation(surroundings=cells, view_size=7, x=10, y=10, energy=100, season=season,
                         season_ticks_left=ticks_left, hunger=hunger, thirst=thirst, carried_food=food,
                         carried_wood=wood, here_amount=here)
    member = Member(npc_id, random.Random(1), PlaceMap(60), tick=0)
    member.map.see(obs, 5)
    script = settlement(build)
    situation = Situation(member, obs, 5)
    return script.choose(situation, script.skills.mask(situation))


def test_thirst_comes_before_hunger_and_both_before_work():
    assert choose({(2, 0): W, (0, 2): F}, thirst=60, hunger=70) == DRINK
    assert choose({(2, 0): W, (0, 2): F}, hunger=70) == EAT
    assert choose({(2, 0): W, (0, 2): F}) == GATHER_FOOD


def test_gatherer_carries_a_full_load_to_the_store():
    assert choose({(2, 0): S, (0, 2): F}, food=4) == STORE_FOOD
    assert choose({(0, 2): F}, food=2) == GATHER_FOOD


def test_gatherer_sows_in_spring_near_a_store():
    assert choose({(2, 0): S}, season=SPRING, food=2) == PLANT
    assert choose({(2, 0): S}, season=SUMMER, food=2) != PLANT


def test_builder_fetches_wood_and_starts_a_store_near_water():
    assert choose({(0, 2): T}, npc_id=0) == GATHER_WOOD
    assert choose({(2, 0): W}, npc_id=0, wood=4) == BUILD_STORE
    assert choose({(0, 0): pb.CELL_SITE_STORE}, npc_id=0, wood=1, here=2) == BUILD_STORE


def test_wood_in_any_hands_goes_to_the_site():
    assert choose({(2, 0): pb.CELL_SITE_STORE}, npc_id=1, wood=1) == BUILD_STORE


def test_before_winter_everyone_takes_food_and_heads_for_a_shelter():
    assert choose({(0, 0): S, (2, 0): H}, season=AUTUMN, ticks_left=50, here=30) == TAKE_FOOD
    assert choose({(0, 0): S, (2, 0): H}, season=AUTUMN, ticks_left=50, here=30, food=3) == TAKE_SHELTER
    assert choose({(2, 0): H}, season=WINTER, food=1) == TAKE_SHELTER
    assert choose({(0, 0): S, (2, 0): H}, season=AUTUMN, ticks_left=300, here=30) != TAKE_SHELTER


def test_without_building_the_scenario_only_gathers_and_explores():
    assert choose({(0, 2): T}, npc_id=0, build=False) == EXPLORE
    assert choose({(0, 2): F, (2, 0): S}, build=False, food=4) == EXPLORE
    assert choose({(0, 2): F}, build=False) == GATHER_FOOD


def test_settlement_answers_for_every_npc_and_forgets_the_dead():
    script = settlement()
    views = [pb.NpcView(npc_id=i, observation=pb.Observation(surroundings=[E] * 49, view_size=7, x=5 + i, y=5,
                                                              energy=100, season=SPRING)) for i in range(3)]

    response = script.step(pb.StepRequest(tick=0, npcs=views))
    script.step(pb.StepRequest(tick=1, npcs=views[:2], retired_npc_ids=[2]))

    assert [action.npc_id for action in response.actions] == [0, 1, 2]
    assert set(script.members) == {0, 1}
