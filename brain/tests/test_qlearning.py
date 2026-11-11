"""Q-learning: the update rule, action choice and what the brain remembers."""

import pytest

from brain.brains import ACTION_COUNT, QLearningBrain
from brain.encoding import encode
from helpers import FOOD, LAVA, NEED_LEVELS, WATER, fixed, observation, rng, view

GAMMA = 0.95
ALPHA = 0.5


def brain(tuning=None, learning=True, seed=1) -> QLearningBrain:
    return QLearningBrain(rng(seed), GAMMA, NEED_LEVELS, tuning or fixed(alpha=ALPHA), learning=learning)


def key(obs) -> bytes:
    return encode(obs, NEED_LEVELS)


def test_update_uses_reward_plus_discounted_best_next_value():
    start, after = observation(), observation(centre=FOOD)
    npc = brain()
    action = npc.act(view(start))
    npc.q[key(after)] = [0.0, 0.0, 3.0, 0.0, 0.0, 0.0]

    npc.act(view(after, reward=1.0))

    assert npc.q[key(start)][action] == pytest.approx(ALPHA * (1.0 + GAMMA * 3.0))


def test_update_after_death_ignores_the_next_state():
    start, respawned = observation(centre=LAVA), observation()
    npc = brain()
    action = npc.act(view(start))
    npc.q[key(respawned)] = [3.0] * ACTION_COUNT

    npc.act(view(respawned, reward=-10.0, died=True))

    assert npc.q[key(start)][action] == pytest.approx(ALPHA * -10.0)


def test_update_after_truncation_bootstraps_from_the_final_observation():
    start, final, respawned = observation(), observation(centre=WATER), observation(centre=FOOD)
    npc = brain()
    action = npc.act(view(start))
    npc.q[key(final)] = [0.0, 2.0, 0.0, 0.0, 0.0, 0.0]
    npc.q[key(respawned)] = [9.0] * ACTION_COUNT

    npc.act(view(respawned, reward=0.1, truncated=True, final=final))

    assert npc.q[key(start)][action] == pytest.approx(ALPHA * (0.1 + GAMMA * 2.0))


def test_truncation_without_final_observation_is_an_error():
    npc = brain()
    npc.act(view())

    with pytest.raises(ValueError, match="final_observation"):
        npc.act(view(reward=0.1, truncated=True))


def test_unknown_next_state_counts_as_zero():
    start, after = observation(), observation(centre=FOOD)
    npc = brain()
    action = npc.act(view(start))

    npc.act(view(after, reward=1.0))

    assert npc.q[key(start)][action] == pytest.approx(ALPHA * 1.0)


def test_first_action_learns_nothing():
    npc = brain()

    npc.act(view(reward=5.0))

    assert npc.q == {key(observation()): [0.0] * ACTION_COUNT}


def test_table_grows_only_by_states_the_npc_was_in():
    final = observation(centre=WATER)
    npc = brain()
    npc.act(view(observation()))

    npc.act(view(observation(centre=FOOD), reward=0.1, truncated=True, final=final))

    assert set(npc.q) == {key(observation()), key(observation(centre=FOOD))}


def test_greedy_choice_takes_the_best_action():
    npc = brain()
    npc.q[key(observation())] = [0.0, 0.0, 0.0, 0.0, 7.0, 0.0]

    assert [npc.act(view(reward=0.0)) for _ in range(20)] == [4] * 20


def test_ties_are_broken_at_random():
    npc = brain(tuning=fixed(alpha=0.0))

    actions = {npc.act(view()) for _ in range(200)}

    assert actions == set(range(ACTION_COUNT))


def test_full_epsilon_explores_even_when_one_action_is_best():
    npc = brain(tuning=fixed(alpha=0.0, epsilon_start=1.0, epsilon_end=1.0))
    npc.q[key(observation())] = [0.0, 0.0, 0.0, 0.0, 7.0, 0.0]

    actions = {npc.act(view()) for _ in range(200)}

    assert actions == set(range(ACTION_COUNT))


def test_epsilon_falls_linearly_and_stops_at_the_end_value():
    schedule = fixed(epsilon_start=1.0, epsilon_end=0.05, decay_ticks=200_000)

    assert schedule.epsilon(0) == pytest.approx(1.0)
    assert schedule.epsilon(100_000) == pytest.approx(0.525)
    assert schedule.epsilon(200_000) == pytest.approx(0.05)
    assert schedule.epsilon(900_000) == pytest.approx(0.05)


def test_show_mode_does_not_learn_and_uses_minimal_epsilon():
    start, after = observation(), observation(centre=FOOD)
    npc = brain(tuning=fixed(epsilon_start=1.0, epsilon_end=0.0), learning=False)
    npc.q[key(start)] = [0.0, 5.0, 0.0, 0.0, 0.0, 0.0]

    actions = [npc.act(view(start)) for _ in range(20)]
    npc.act(view(after, reward=100.0))

    assert actions == [1] * 20
    assert npc.q[key(start)] == [0.0, 5.0, 0.0, 0.0, 0.0, 0.0]


def test_new_run_drops_the_unfinished_transition():
    npc = brain()
    npc.act(view())

    npc.start_run()
    npc.act(view(observation(centre=FOOD), reward=50.0))

    assert npc.q[key(observation())] == [0.0] * ACTION_COUNT


def test_same_seed_gives_the_same_brain():
    def run(seed):
        npc = brain(tuning=fixed(epsilon_start=1.0, epsilon_end=0.05, decay_ticks=50), seed=seed)
        centres = [FOOD, LAVA, WATER]
        actions = [npc.act(view(observation(centre=centres[i % 3], hunger=i % 100), reward=i % 7)) for i in range(300)]
        return actions, npc.q

    assert run(5) == run(5)
    assert run(5) != run(6)
