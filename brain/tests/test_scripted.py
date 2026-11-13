"""The hand-written settlement of step 3.6: its memory, its paths and its priorities."""

import dataclasses

import pytest

from brain.config import REPO_ROOT, load_config
from brain.proto import brain_pb2 as pb
from brain.scripted import (
    LEFT,
    RIGHT,
    STAY,
    UP,
    WINTER,
    ScenarioConfig,
    ScriptedSettlement,
    SettlementMemory,
    step_along,
)

E, F, W, T, S, H, L, X = (pb.CELL_EMPTY, pb.CELL_FOOD, pb.CELL_WATER, pb.CELL_TREE, pb.CELL_STORE,
                          pb.CELL_SHELTER, pb.CELL_LAVA, pb.CELL_WALL)

CONFIG = ScenarioConfig(build=True, season_ticks=500, consume_relief=40, shelter_capacity=4,
                        store_capacity=100, shelter_wood=6, store_wood=4)


def observation(x, y, cells=None, *, hunger=0, thirst=0, season=1, ticks_left=400, food=0, wood=0, here=0):
    """A 3x3 view (centre = the NPC's cell), empty unless `cells` says otherwise."""
    return pb.Observation(surroundings=cells or [E] * 9, view_size=3, x=x, y=y, hunger=hunger, thirst=thirst,
                          energy=100, season=season, season_ticks_left=ticks_left, carried_food=food,
                          carried_wood=wood, here_amount=here)


def request(tick, *observations):
    return pb.StepRequest(tick=tick, npcs=[pb.NpcView(npc_id=i, observation=o) for i, o in enumerate(observations)])


def act(settlement, tick, obs):
    return settlement.step(request(tick, obs)).actions[0].action


def corridor(memory: SettlementMemory, cells: dict) -> None:
    memory.cells.update(cells)
    memory.begin_tick()


# --- memory and paths -------------------------------------------------------------------------

def test_memory_places_the_view_on_the_board():
    memory = SettlementMemory()

    memory.see(observation(5, 5, [W, E, E, E, S, E, E, E, F], here=37), tick=0)

    assert memory.cells[(4, 4)] == W
    assert memory.cells[(6, 6)] == F
    assert memory.store_food[(5, 5)] == 37


def test_step_along_moves_closer_and_stops_on_the_target():
    memory = SettlementMemory()
    corridor(memory, {(x, 1): E for x in range(1, 6)})

    field = memory.distances(("t",), [(5, 1)])

    assert step_along(field, (1, 1)) == RIGHT
    assert step_along(field, (5, 1)) is None


def test_paths_go_around_lava():
    memory = SettlementMemory()
    corridor(memory, {(1, 1): E, (2, 1): L, (3, 1): E, (1, 2): E, (2, 2): E, (3, 2): E})

    field = memory.distances(("t",), [(3, 1)])

    assert field[(1, 1)] == 4
    assert (2, 1) not in field


def test_winter_rot_is_remembered_and_food_spots_come_back_after_a_while():
    memory = SettlementMemory()
    memory.see(observation(5, 5, [F] + [E] * 8), tick=0)

    memory.forget_wild_food(tick=100)

    assert memory.cells[(4, 4)] == E
    memory.begin_tick()
    assert memory.wild_targets(tick=150, retry_ticks=90) == []
    memory.begin_tick()
    assert memory.wild_targets(tick=190, retry_ticks=90) == [(4, 4)]


# --- priorities of one NPC ---------------------------------------------------------------------

def test_thirsty_npc_drinks_on_water_and_walks_to_it_otherwise():
    settlement = ScriptedSettlement(CONFIG)

    assert act(settlement, 0, observation(5, 5, [E, E, E, E, W, E, E, E, E], thirst=30)) == pb.ACTION_CONSUME
    assert act(settlement, 1, observation(5, 5, [E, W, E, E, E, E, E, E, E], thirst=60)) == UP


def test_hungry_npc_eats_where_food_is_even_with_full_hands():
    settlement = ScriptedSettlement(CONFIG)

    on_store = observation(5, 5, [E, E, E, E, S, E, E, E, E], hunger=60, wood=4, here=10)
    assert act(settlement, 0, on_store) == pb.ACTION_CONSUME
    assert act(settlement, 1, observation(5, 5, hunger=60, food=1)) == pb.ACTION_EAT_CARRIED


def test_hungry_npc_prefers_seen_food_over_a_store_nearby():
    settlement = ScriptedSettlement(CONFIG)
    view = [E, E, E, S, E, E, E, E, F]   # store to the left, food down-right

    assert act(settlement, 0, observation(5, 5, view, hunger=60)) in (pb.ACTION_DOWN, RIGHT)


def test_in_winter_npcs_go_to_a_shelter_and_stay_there():
    settlement = ScriptedSettlement(CONFIG)
    view = [E, E, E, H, E, E, E, E, E]

    assert act(settlement, 0, observation(5, 5, view, season=WINTER)) == LEFT
    assert act(settlement, 1, observation(4, 5, [E, E, E, E, H, E, E, E, E], season=WINTER)) == STAY


def test_a_store_takes_food_but_never_traps_an_npc_carrying_wood():
    settlement = ScriptedSettlement(CONFIG)
    view = [E, E, E, E, S, H, E, E, E]

    assert act(settlement, 0, observation(5, 5, view, season=WINTER, food=3, wood=1)) == pb.ACTION_DEPOSIT
    assert act(settlement, 1, observation(5, 5, view, season=WINTER, wood=4, here=50)) == RIGHT


def test_without_building_nobody_builds_or_deposits():
    settlement = ScriptedSettlement(dataclasses.replace(CONFIG, build=False))
    view = [E, E, E, E, S, E, E, E, E]

    action = act(settlement, 0, observation(5, 5, view, food=4))

    assert action not in (pb.ACTION_DEPOSIT, pb.ACTION_BUILD_SHELTER, pb.ACTION_BUILD_STORE, pb.ACTION_PLANT)
    assert settlement.planned_sites == {}


def test_home_is_an_empty_cell_near_water():
    settlement = ScriptedSettlement(CONFIG)

    settlement.step(request(0, observation(5, 5, [W, E, E, E, E, E, E, E, E])))

    assert settlement.home is not None
    assert settlement.memory.cells[settlement.home] == E
    assert abs(settlement.home[0] - 4) + abs(settlement.home[1] - 4) <= 2


# --- configuration -----------------------------------------------------------------------------

def test_stage_three_config_builds_the_scenario():
    config = load_config(REPO_ROOT / "config" / "stage-3.yaml")

    assert config.kind == "scripted"
    assert config.scenario.build is True
    assert config.scenario.season_ticks == 500
    assert config.scenario.store_capacity == 100


def test_earlier_configs_have_no_scenario():
    assert load_config().scenario is None
    with pytest.raises(ValueError, match="scripted"):
        from brain.server import build_group

        build_group(dataclasses.replace(load_config(), kind="scripted"))
