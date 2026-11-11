"""Self-tuning: the NPC derives epsilon and alpha from its own visit counters."""

import dataclasses
import math

import pytest

from brain.brains import ACTION_COUNT, QLearningBrain
from brain.config import load_config
from brain.encoding import encode
from brain.server import build_tuning
from brain.tuning import FixedSchedule, SelfTuning
from helpers import FOOD, NEED_LEVELS, fixed, observation, rng, self_tuning, view

GAMMA = 0.95


def brain(tuning, learning=True) -> QLearningBrain:
    return QLearningBrain(rng(), GAMMA, NEED_LEVELS, tuning, learning=learning)


def key(obs) -> bytes:
    return encode(obs, NEED_LEVELS)


def test_epsilon_is_one_in_a_new_situation_and_falls_with_visits():
    tuning = self_tuning(epsilon_min=0.05)

    assert tuning.epsilon(steps=0, state_visits=0) == pytest.approx(1.0)
    assert tuning.epsilon(steps=0, state_visits=3) == pytest.approx(0.5)
    assert tuning.epsilon(steps=0, state_visits=99) == pytest.approx(0.1)
    epsilons = [tuning.epsilon(0, visits) for visits in range(1000)]
    assert epsilons == sorted(epsilons, reverse=True)


def test_epsilon_never_falls_below_the_floor():
    tuning = self_tuning(epsilon_min=0.05)

    assert tuning.epsilon(steps=0, state_visits=399) == pytest.approx(0.05)
    assert tuning.epsilon(steps=0, state_visits=10**9) == 0.05


def test_epsilon_depends_on_visits_not_on_age():
    tuning = self_tuning()

    assert tuning.epsilon(steps=0, state_visits=8) == tuning.epsilon(steps=10**6, state_visits=8)


def test_alpha_is_one_on_the_first_try_and_falls_with_tries():
    tuning = self_tuning(alpha_min=0.1)

    assert tuning.alpha(pair_tries=1) == pytest.approx(1.0)
    assert tuning.alpha(pair_tries=4) == pytest.approx(0.25)
    assert tuning.alpha(pair_tries=10) == pytest.approx(0.1)


def test_alpha_never_falls_below_the_floor():
    tuning = self_tuning(alpha_min=0.1)

    assert tuning.alpha(pair_tries=11) == 0.1
    assert tuning.alpha(pair_tries=10**9) == 0.1


def test_brain_counts_visits_per_situation():
    npc = brain(self_tuning())
    home, meadow = observation(), observation(centre=FOOD)

    for obs in (home, home, meadow, home):
        npc.act(view(obs))

    assert npc.state_visits == {key(home): 3, key(meadow): 1}


def test_brain_counts_tries_per_situation_and_action():
    npc = brain(self_tuning())
    home = observation()

    actions = [npc.act(view(home)) for _ in range(30)]

    # The last action has no outcome yet, so it is not counted.
    expected = [actions[:-1].count(action) for action in range(ACTION_COUNT)]
    assert npc.pair_tries == {key(home): expected}


def test_first_update_takes_the_whole_target_and_later_ones_average():
    npc = brain(self_tuning(epsilon_min=0.0, alpha_min=0.0))
    home = observation()
    # Lock the brain onto one action: a familiar situation with a clear favourite.
    npc.state_visits[key(home)] = 10**9
    npc.q[key(home)] = [0.0, 0.0, 0.0, 1e-9, 0.0, 0.0]
    dead_end = dict(died=True)  # terminal targets: the value is the plain average of rewards

    npc.act(view(home))
    npc.act(view(home, reward=4.0, **dead_end))
    assert npc.q[key(home)][3] == pytest.approx(4.0)

    npc.act(view(home, reward=2.0, **dead_end))
    assert npc.q[key(home)][3] == pytest.approx(3.0)

    npc.act(view(home, reward=6.0, **dead_end))
    assert npc.q[key(home)][3] == pytest.approx(4.0)


def test_new_situation_is_explored_and_familiar_one_exploited():
    npc = brain(self_tuning(epsilon_min=0.0, alpha_min=0.0))
    home = observation()
    npc.q[key(home)] = [0.0, 0.0, 0.0, 0.0, 5.0, 0.0]

    npc.state_visits[key(home)] = 10**9
    # Terminal updates with reward 5 keep the favourite's value where it is.
    familiar = {npc.act(view(home, reward=5.0, died=True)) for _ in range(50)}
    assert familiar == {4}

    explorer = brain(self_tuning(epsilon_min=1.0))
    explorer.q[key(home)] = [0.0, 0.0, 0.0, 0.0, 5.0, 0.0]
    assert {explorer.act(view(home, died=True)) for _ in range(200)} == set(range(ACTION_COUNT))


def test_show_mode_leaves_counters_alone():
    npc = brain(self_tuning(), learning=False)

    for _ in range(5):
        npc.act(view(reward=1.0))

    assert npc.state_visits == {}
    assert npc.pair_tries == {}


def test_fixed_schedule_ignores_the_counters():
    schedule = fixed(alpha=0.1, epsilon_start=1.0, epsilon_end=0.05, decay_ticks=200_000)

    assert schedule.epsilon(100_000, state_visits=0) == schedule.epsilon(100_000, state_visits=5000)
    assert schedule.alpha(1) == schedule.alpha(5000) == 0.1


def test_config_switch_selects_the_variant():
    config = load_config()

    assert isinstance(build_tuning(dataclasses.replace(config, tuning="self")), SelfTuning)
    assert isinstance(build_tuning(dataclasses.replace(config, tuning="fixed")), FixedSchedule)
    with pytest.raises(ValueError, match="tuning"):
        build_tuning(dataclasses.replace(config, tuning="other"))


def test_stage_config_uses_self_tuning_with_the_documented_floors():
    config = load_config()
    tuning = build_tuning(config)

    assert config.tuning == "self"
    assert tuning.epsilon(0, 10**9) == 0.05
    assert tuning.alpha(10**9) == 0.1
    assert math.isclose(tuning.epsilon(0, 0), 1.0)
