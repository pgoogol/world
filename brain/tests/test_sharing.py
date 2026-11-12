"""Stage 2: brains are born and retire, newborns inherit, neighbours learn from each other and swap maps."""

import dataclasses

import pytest

from brain.brains import ACTION_COUNT, FOLLOW_MAP, QLearningBrain
from brain.config import SharingConfig, load_config
from brain.discovery import DiscoveryMap
from brain.encoding import encode
from brain.group import BrainGroup
from brain.proto import brain_pb2
from brain.server import build_group
from brain.storage import load_brains, save_brains
from helpers import EMPTY, FOOD, NEED_LEVELS, WATER, fixed, observation, rng, view

GAMMA = 0.95
HOME = observation()
MEADOW = observation(centre=FOOD)


def key(obs) -> bytes:
    return encode(obs, NEED_LEVELS)


def brain(seed=1, with_map=False, alpha=0.5) -> QLearningBrain:
    discovery = DiscoveryMap(food_retry_ticks=50) if with_map else None
    return QLearningBrain(rng(seed), GAMMA, NEED_LEVELS, fixed(alpha=alpha), discovery=discovery)


def group(sharing: SharingConfig, with_map=False) -> BrainGroup:
    seeds = iter(range(1, 100))
    return BrainGroup(lambda npc_id: brain(next(seeds), with_map=with_map), sharing=sharing)


def request(tick, views, retired=()) -> brain_pb2.StepRequest:
    return brain_pb2.StepRequest(tick=tick, npcs=views, retired_npc_ids=list(retired))


def elder(q_values, tries, visits=10) -> QLearningBrain:
    result = brain()
    result.q[key(HOME)] = list(q_values)
    result.pair_tries[key(HOME)] = list(tries)
    result.state_visits[key(HOME)] = visits
    return result


# --- life cycle ---------------------------------------------------------------------------------

def test_retired_npc_loses_its_brain_and_a_new_id_gets_a_fresh_one():
    brains = group(SharingConfig())
    brains.step(request(0, [view(npc_id=0), view(npc_id=1)]))
    first = brains.brains[1]

    brains.step(request(1, [view(npc_id=0), view(npc_id=2)], retired=[1]))

    assert set(brains.brains) == {0, 2}
    assert brains.brains[2] is not first
    assert brains.brains[2].steps == 1


def test_retiring_an_unknown_npc_is_harmless():
    brains = group(SharingConfig())

    brains.step(request(0, [view(npc_id=0)], retired=[7]))

    assert set(brains.brains) == {0}


# --- inheritance --------------------------------------------------------------------------------

def test_inherited_value_is_the_average_weighted_by_tries():
    first = elder([0, 4.0, 0, 0, 0, 0], [0, 30, 0, 0, 0, 0])
    second = elder([0, 8.0, 0, 0, 0, 0], [0, 10, 0, 0, 0, 0])
    newborn = brain()

    newborn.inherit([first, second], trust=0.25)

    assert newborn.q[key(HOME)][1] == pytest.approx((30 * 4.0 + 10 * 8.0) / 40)


def test_actions_no_elder_tried_stay_unknown():
    newborn = brain()

    newborn.inherit([elder([9.0, 4.0, 0, 0, 0, 0], [0, 30, 0, 0, 0, 0])], trust=0.25)

    assert newborn.q[key(HOME)][0] == 0.0
    assert newborn.pair_tries[key(HOME)][0] == 0


def test_inherited_counters_are_scaled_by_trust():
    first = elder([0, 4.0, 0, 0, 0, 0], [0, 30, 0, 0, 0, 0], visits=100)
    second = elder([0, 8.0, 0, 0, 0, 0], [0, 10, 0, 0, 0, 0], visits=60)
    newborn = brain()

    newborn.inherit([first, second], trust=0.25)

    assert newborn.pair_tries[key(HOME)][1] == int(0.25 * (30 + 10) / 2)
    assert newborn.state_visits[key(HOME)] == int(0.25 * (100 + 60) / 2)


def test_only_the_elder_who_knows_a_situation_counts_for_it():
    knows_home = elder([0, 4.0, 0, 0, 0, 0], [0, 20, 0, 0, 0, 0])
    knows_meadow = brain()
    knows_meadow.q[key(MEADOW)] = [0, 0, 0, 0, 0, 6.0]
    knows_meadow.pair_tries[key(MEADOW)] = [0, 0, 0, 0, 0, 8]
    newborn = brain()

    newborn.inherit([knows_home, knows_meadow], trust=0.5)

    assert newborn.q[key(HOME)][1] == pytest.approx(4.0)
    assert newborn.pair_tries[key(HOME)][1] == 10
    assert newborn.q[key(MEADOW)][5] == pytest.approx(6.0)
    assert newborn.pair_tries[key(MEADOW)][5] == 4


def test_newborn_inherits_the_map_too():
    first, second = brain(with_map=True), brain(with_map=True)
    first.discovery.water = {(1, 1)}
    second.discovery.food = {(2, 2): (5, True)}
    newborn = brain(with_map=True)

    newborn.inherit([first, second], trust=0.25)

    assert newborn.discovery.water == {(1, 1)}
    assert newborn.discovery.food == {(2, 2): (5, True)}


def teach(brains: BrainGroup, npc_id: int) -> None:
    brains.brains[npc_id].q[key(HOME)] = [0, 5.0, 0, 0, 0, 0]
    brains.brains[npc_id].pair_tries[key(HOME)] = [0, 40, 0, 0, 0, 0]


def test_newborn_inherits_only_from_its_neighbours():
    brains = group(SharingConfig(inherit=True, inherit_trust=0.5))
    brains.step(request(0, [view(npc_id=0), view(npc_id=1)]))
    teach(brains, 0)
    brains.brains[1].q[key(HOME)] = [0, 99.0, 0, 0, 0, 0]
    brains.brains[1].pair_tries[key(HOME)] = [0, 40, 0, 0, 0, 0]

    brains.step(request(1, [view(npc_id=0), view(npc_id=1), view(MEADOW, npc_id=2, neighbours=[0])]))

    assert brains.brains[2].q[key(HOME)][1] == pytest.approx(5.0)
    assert brains.brains[2].pair_tries[key(HOME)][1] == 20


def test_newborn_without_neighbours_starts_empty():
    brains = group(SharingConfig(inherit=True))
    brains.step(request(0, [view(npc_id=0)]))
    teach(brains, 0)

    brains.step(request(1, [view(npc_id=0), view(MEADOW, npc_id=2)]))

    assert key(HOME) not in brains.brains[2].q


def test_inheritance_switch_off_leaves_the_newborn_empty():
    brains = group(SharingConfig(inherit=False))
    brains.step(request(0, [view(npc_id=0)]))
    teach(brains, 0)

    brains.step(request(1, [view(npc_id=0), view(MEADOW, npc_id=2, neighbours=[0])]))

    assert key(HOME) not in brains.brains[2].q


def test_founders_born_together_do_not_inherit_from_each_other():
    brains = group(SharingConfig(inherit=True))

    brains.step(request(0, [view(npc_id=0, neighbours=[1]), view(npc_id=1, neighbours=[0])]))

    assert brains.brains[0].pair_tries == {}
    assert brains.brains[1].pair_tries == {}


# --- learning from a neighbour ------------------------------------------------------------------

def test_learn_from_updates_the_table_with_a_reduced_rate():
    observer = brain(alpha=0.5)
    observer.q[key(MEADOW)] = [0, 0, 2.0, 0, 0, 0]

    observer.learn_from((key(HOME), 3, 1.0, key(MEADOW)), weight=0.5)

    assert observer.q[key(HOME)][3] == pytest.approx(0.5 * 0.5 * (1.0 + GAMMA * 2.0))
    assert observer.pair_tries[key(HOME)][3] == 1


def test_learn_from_a_collapse_uses_the_bare_reward():
    observer = brain(alpha=0.5)

    observer.learn_from((key(HOME), 3, -10.0, None), weight=0.5)

    assert observer.q[key(HOME)][3] == pytest.approx(0.25 * -10.0)


def test_brain_exposes_the_transition_it_has_just_finished():
    npc = brain()
    first_action = npc.act(view(HOME))
    assert npc.last_transition is None

    npc.act(view(MEADOW, reward=1.0))
    assert npc.last_transition == (key(HOME), first_action, 1.0, key(MEADOW))

    npc.act(view(HOME, reward=-10.0, died=True))
    assert npc.last_transition[3] is None


def two_npcs_step(brains: BrainGroup, tick: int, neighbours: bool) -> None:
    brains.step(request(tick, [
        view(HOME if tick % 2 == 0 else MEADOW, reward=1.0, npc_id=0, neighbours=[1] if neighbours else []),
        view(observation(centre=WATER), reward=0.0, npc_id=1, neighbours=[0] if neighbours else []),
    ]))


def test_neighbour_learns_from_the_other_npcs_step():
    brains = group(SharingConfig(observe=True, observe_weight=0.5))
    two_npcs_step(brains, 0, neighbours=True)

    two_npcs_step(brains, 1, neighbours=True)

    actor_transition = brains.brains[0].last_transition
    observer = brains.brains[1]
    assert actor_transition[0] == key(HOME)
    assert observer.pair_tries[key(HOME)][actor_transition[1]] == 1
    assert observer.q[key(HOME)][actor_transition[1]] != 0.0


def test_npcs_that_are_not_neighbours_do_not_learn_from_each_other():
    brains = group(SharingConfig(observe=True))
    two_npcs_step(brains, 0, neighbours=False)

    two_npcs_step(brains, 1, neighbours=False)

    assert key(HOME) not in brains.brains[1].pair_tries


def test_observation_switch_off_leaves_the_neighbour_alone():
    brains = group(SharingConfig(observe=False))
    two_npcs_step(brains, 0, neighbours=True)

    two_npcs_step(brains, 1, neighbours=True)

    assert key(HOME) not in brains.brains[1].pair_tries


# --- maps ---------------------------------------------------------------------------------------

def test_neighbours_swap_maps_in_both_directions():
    brains = group(SharingConfig(map=True), with_map=True)
    water_view = observation(x=2, y=2, cells=[WATER] + [EMPTY] * 8)
    food_view = observation(x=3, y=3, cells=[EMPTY] * 8 + [FOOD])

    brains.step(request(0, [view(water_view, npc_id=0, neighbours=[1]), view(food_view, npc_id=1, neighbours=[0])]))

    for npc_id in (0, 1):
        assert brains.brains[npc_id].discovery.water == {(1, 1)}
        assert brains.brains[npc_id].discovery.food == {(4, 4): (0, True)}


def test_maps_stay_private_without_contact_or_with_the_switch_off():
    water_view = observation(x=2, y=2, cells=[WATER] + [EMPTY] * 8)

    apart = group(SharingConfig(map=True), with_map=True)
    apart.step(request(0, [view(water_view, npc_id=0), view(observation(x=9, y=9), npc_id=1)]))
    assert apart.brains[1].discovery.water == set()

    switched_off = group(SharingConfig(map=False), with_map=True)
    switched_off.step(request(0, [view(water_view, npc_id=0, neighbours=[1]), view(npc_id=1, neighbours=[0])]))
    assert switched_off.brains[1].discovery.water == set()


def test_key_says_whether_a_place_is_known_only_with_a_map():
    plain, mapped, clueless = brain(), brain(with_map=True), brain(with_map=True)
    thirsty = observation(x=5, y=5, thirst=60)
    mapped.discovery.water = {(5, 1)}

    plain.act(view(thirsty))
    mapped.act(view(thirsty))
    clueless.act(view(thirsty))

    assert list(plain.q) == [key(thirsty)]
    assert list(mapped.q) == [key(thirsty) + b"\x01"]
    assert list(clueless.q) == [key(thirsty) + b"\x00"]


def test_brain_with_a_map_has_a_seventh_choice_only_when_a_place_is_known():
    mapped = brain(with_map=True)
    thirsty = observation(x=5, y=5, thirst=60)
    mapped.discovery.water = {(5, 1)}
    known, unknown = key(thirsty) + b"\x01", key(observation(x=5, y=5)) + b"\x00"
    mapped.q[known] = [0, 0, 0, 0, 0, 0, 9.0]
    mapped.q[unknown] = [0, 0, 0, 0, 0, 0, 9.0]

    # Greedy brain (epsilon 0): the seventh value wins where the place is known and turns into a step up.
    assert [mapped.act(view(thirsty, died=True)) for _ in range(10)] == [1] * 10
    assert mapped._previous == (known, FOLLOW_MAP)

    # Without a need there is no place to walk to, so the seventh value is ignored.
    actions = {mapped.act(view(observation(x=5, y=5), died=True)) for _ in range(200)}
    assert actions == set(range(ACTION_COUNT))
    assert mapped._previous[1] < FOLLOW_MAP


def test_following_the_map_ends_with_consuming_on_the_place():
    mapped = brain(with_map=True)
    at_water = observation(x=5, y=5, thirst=60, centre=WATER)
    mapped.q[key(at_water) + b"\x01"] = [0, 0, 0, 0, 0, 0, 9.0]

    assert mapped.act(view(at_water)) == 5


def test_unused_seventh_value_does_not_count_as_the_best_next_value():
    mapped = brain(with_map=True, alpha=1.0)
    start, calm = observation(x=5, y=5, hunger=10), observation(x=5, y=6, hunger=11)
    action = mapped.act(view(start))
    mapped.q[key(calm) + b"\x00"] = [-1.0, -2.0, -3.0, -1.5, -2.5, -4.0, 0.0]

    mapped.act(view(calm, reward=0.0))

    assert mapped.q[key(start) + b"\x00"][action] == pytest.approx(GAMMA * -1.0)


def test_map_survives_saving_and_loading(tmp_path):
    npc = brain(with_map=True)
    npc.act(view(observation(x=2, y=2, cells=[WATER] + [EMPTY] * 7 + [FOOD])), tick=3)
    save_brains(tmp_path / "tables.pkl", {0: npc})

    restored = brain(with_map=True)
    restored.import_state(load_brains(tmp_path / "tables.pkl")[0])

    assert restored.discovery.water == {(1, 1)}
    assert restored.discovery.food == {(3, 3): (3, True)}


# --- configuration ------------------------------------------------------------------------------

def test_stage_one_config_has_all_sharing_off():
    sharing = load_config().sharing

    assert (sharing.inherit, sharing.observe, sharing.map) == (False, False, False)


def test_stage_two_config_turns_sharing_on(tmp_path):
    from brain.config import REPO_ROOT

    config = load_config(REPO_ROOT / "config" / "stage-2.yaml")
    brains = build_group(config, data_dir=tmp_path, fresh=True)
    brains.step(request(0, [view(npc_id=0)]))

    assert config.sharing == SharingConfig(True, 0.25, True, 0.5, True, 50)
    assert brains.brains[0].discovery is not None


def test_control_settlement_has_no_map_in_the_key(tmp_path):
    from brain.config import REPO_ROOT

    config = load_config(REPO_ROOT / "config" / "stage-2.yaml")
    config = dataclasses.replace(config, sharing=SharingConfig())
    brains = build_group(config, data_dir=tmp_path, fresh=True)
    brains.step(request(0, [view(npc_id=0)]))

    assert brains.brains[0].discovery is None
    assert all(len(state) == 11 for state in brains.brains[0].q)
    assert all(len(values) == ACTION_COUNT for values in brains.brains[0].q.values())
