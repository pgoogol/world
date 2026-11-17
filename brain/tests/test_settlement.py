"""Step 3.9: the settlement network answers Brain.Step, learns with PPO and repeats itself on the same seed."""

import dataclasses

import pytest

torch = pytest.importorskip("torch")

from brain.config import REPO_ROOT, NetworkConfig, load_config  # noqa: E402
from brain.ppo import Ppo, Step, advantages_of  # noqa: E402
from brain.proto import brain_pb2 as pb  # noqa: E402
from brain.server import build_group  # noqa: E402
from brain.settlement import SCALARS, NetworkSettlement  # noqa: E402

E, F, W = pb.CELL_EMPTY, pb.CELL_FOOD, pb.CELL_WATER
VALID = set(range(pb.ACTION_STAY, pb.ACTION_WITHDRAW + 1))


def config(choices="actions", rollout=4, **network):
    base = load_config(REPO_ROOT / "config" / "stage-2.yaml")
    settings = dataclasses.replace(base.network, choices=choices, rollout_ticks=rollout, minibatch=64, **network)
    return dataclasses.replace(base, kind="network", network=settings)


def view(npc_id=0, *, reward=0.0, died=False, x=5, y=5, cells=None, thirst=0, neighbours=()):
    observation = pb.Observation(surroundings=cells or [E] * 9, view_size=3, x=x, y=y, energy=100, thirst=thirst)
    return pb.NpcView(npc_id=npc_id, observation=observation, reward=reward, died=died, neighbour_ids=neighbours)


def request(tick, *views, retired=()):
    return pb.StepRequest(tick=tick, npcs=views, retired_npc_ids=retired)


def test_answers_with_one_valid_action_per_npc():
    settlement = NetworkSettlement(config())

    response = settlement.step(request(0, view(0), view(1), view(2)))

    assert [action.npc_id for action in response.actions] == [0, 1, 2]
    assert all(action.action in VALID for action in response.actions)


def test_same_seed_gives_the_same_actions_and_the_same_network():
    def run():
        settlement = NetworkSettlement(config())
        actions = []
        for tick in range(40):
            views = [view(i, reward=0.1 * (tick % 3), x=3 + i, thirst=tick) for i in range(4)]
            actions += [a.action for a in settlement.step(request(tick, *views)).actions]
        return actions, settlement.ppo.network.state_dict()

    first_actions, first_weights = run()
    second_actions, second_weights = run()

    assert first_actions == second_actions
    assert all(torch.equal(first_weights[name], second_weights[name]) for name in first_weights)


def test_network_is_updated_once_the_rollout_is_full():
    settlement = NetworkSettlement(config(rollout=4))

    for tick in range(3):
        settlement.step(request(tick, view(0), view(1)))
    assert settlement.ppo.updates == 0
    settlement.step(request(3, view(0), view(1)))

    assert settlement.ppo.updates == 1


def test_show_mode_neither_learns_nor_keeps_experience():
    settlement = NetworkSettlement(config(rollout=2), learning=False)

    for tick in range(10):
        settlement.step(request(tick, view(0, reward=1.0)))

    assert settlement.ppo.updates == 0
    assert settlement.members[0].steps == []


def test_death_ends_the_piece_of_experience():
    settlement = NetworkSettlement(config(rollout=100))
    settlement.step(request(0, view(0)))

    settlement.step(request(1, view(0, reward=-10.0, died=True)))

    steps = settlement.members[0].steps
    assert [(step.reward, step.done) for step in steps] == [(-10.0, True)]


def test_retired_npc_leaves_its_experience_but_not_its_memory():
    settlement = NetworkSettlement(config(rollout=100))
    settlement.step(request(0, view(0), view(1)))
    settlement.step(request(1, view(0, reward=0.1), view(1, reward=0.1)))

    settlement.step(request(2, view(1, reward=0.1), retired=[0]))

    assert set(settlement.members) == {1}
    assert len(settlement._finished) == 1 and len(settlement._finished[0][0]) == 1


def test_tick_zero_starts_a_new_run_with_fresh_members():
    settlement = NetworkSettlement(config(rollout=100))
    settlement.step(request(0, view(0, cells=[W] + [E] * 8)))
    settlement.step(request(1, view(0, reward=0.1)))

    settlement.step(request(0, view(0)))

    assert settlement.members[0].map.places(pb.CELL_WATER) == []
    assert len(settlement._finished) == 1


def test_neighbours_swap_their_place_maps():
    settlement = NetworkSettlement(config())

    settlement.step(request(0, view(0, x=5, y=5, cells=[W] + [E] * 8, neighbours=[1]),
                            view(1, x=6, y=5, neighbours=[0])))

    assert settlement.members[1].map.places(pb.CELL_WATER) == [(4, 4)]


def test_saved_network_is_loaded_back(tmp_path):
    path = tmp_path / "network.pt"
    trained = NetworkSettlement(config(rollout=2), save_path=path)
    for tick in range(6):
        trained.step(request(tick, view(0, reward=1.0), view(1, reward=-1.0)))
    trained.save()

    loaded = NetworkSettlement(config(), learning=False, save_path=path, load=True)

    weights, restored = trained.ppo.network.state_dict(), loaded.ppo.network.state_dict()
    assert all(torch.equal(weights[name], restored[name]) for name in weights)


def test_server_builds_a_network_settlement_with_skills():
    base = load_config(REPO_ROOT / "config" / "stage-3.yaml")

    group = build_group(dataclasses.replace(base, kind="network"), fresh=True)

    assert isinstance(group, NetworkSettlement)
    assert group.choices.names[0] == "drink"
    assert group.ppo.network.body[0].in_features == 49 * 14 + SCALARS


def test_ppo_learns_which_choice_pays():
    """A two-armed bandit: after a few updates the network prefers the arm that pays."""
    ppo = Ppo(view_cells=1, scalars=1, choices=2, config=NetworkConfig(minibatch=64, learning_rate=0.01), seed=1)
    cells, scalars, mask = [[1]], [[0.0]], [[True, True]]

    for _ in range(30):
        pieces = []
        for _ in range(64):
            choice, log_prob, value = (column[0] for column in ppo.choose(cells, scalars, mask))
            pieces.append(([Step(cells[0], scalars[0], mask[0], choice, log_prob, value,
                                 reward=1.0 if choice == 1 else 0.0, done=True)], 0.0))
        ppo.update(pieces)

    logits, _ = ppo.evaluate(cells, scalars, mask)
    assert torch.softmax(logits, dim=1)[0, 1] > 0.9


def test_ppo_learns_even_when_values_are_large():
    """Rewards far from zero make the value loss huge; the policy still learns which arm pays."""
    ppo = Ppo(view_cells=1, scalars=1, choices=2, config=NetworkConfig(minibatch=64, learning_rate=0.01), seed=1)
    cells, scalars, mask = [[1]], [[0.0]], [[True, True]]

    for _ in range(30):
        pieces = []
        for _ in range(64):
            choice, log_prob, value = (column[0] for column in ppo.choose(cells, scalars, mask))
            pieces.append(([Step(cells[0], scalars[0], mask[0], choice, log_prob, value,
                                 reward=1000.0 + (1.0 if choice == 1 else 0.0), done=True)], 0.0))
        ppo.update(pieces)

    logits, _ = ppo.evaluate(cells, scalars, mask)
    assert torch.softmax(logits, dim=1)[0, 1] > 0.9


def test_ppo_never_picks_a_masked_choice():
    ppo = Ppo(view_cells=1, scalars=1, choices=3, config=NetworkConfig(), seed=1)

    choices, _, _ = ppo.choose([[1]] * 200, [[0.0]] * 200, [[False, True, False]] * 200)

    assert set(choices) == {1}


def test_advantage_does_not_leak_across_a_death():
    """The step after a death starts a new life: its rewards say nothing about the steps before."""
    steps = [Step([1], [0.0], [True, True], 0, -0.7, 0.0, reward=-10.0, done=True),
             Step([1], [0.0], [True, True], 0, -0.7, 0.0, reward=100.0, done=False)]

    assert advantages_of(steps, bootstrap=0.0, gae_lambda=0.95) == [-10.0, 100.0]


def test_advantage_counts_the_value_of_what_comes_next():
    steps = [Step([1], [0.0], [True, True], 0, -0.7, value=1.0, reward=0.5, discount=0.9),
             Step([1], [0.0], [True, True], 0, -0.7, value=2.0, reward=0.0, discount=0.9)]

    last = 0.9 * 4.0 - 2.0
    first = 0.5 + 0.9 * 2.0 - 1.0 + 0.9 * 1.0 * last
    assert advantages_of(steps, bootstrap=4.0, gae_lambda=1.0) == pytest.approx([first, last])


def test_npc_keeps_its_skill_for_several_ticks_and_the_rewards_add_up():
    settlement = NetworkSettlement(config(choices="skills", rollout=100, skill_ticks=3, gamma=0.5))
    settlement.step(request(0, view(0)))
    member = settlement.members[0]
    member.choice = first = settlement.choices.names.index("explore")   # a skill that only walks
    member.decide_at = 3

    settlement.step(request(1, view(0, reward=1.0)))
    settlement.step(request(2, view(0, reward=1.0)))
    assert member.choice == first and member.steps == []
    settlement.step(request(3, view(0, reward=1.0)))

    (step,) = member.steps
    assert step.reward == pytest.approx(1.0 + 0.5 + 0.25)
    assert step.discount == pytest.approx(0.125)


def test_npc_chooses_again_as_soon_as_its_skill_stops_being_possible():
    settlement = NetworkSettlement(config(choices="skills", rollout=100, skill_ticks=50))
    water = [W] + [E] * 8
    settlement.step(request(0, view(0, cells=water)))
    member = settlement.members[0]
    member.choice = 0                                  # drink: walk to the water in sight
    member.map._set((4, 4), E, 1, 0)                   # the water turns out to be gone

    settlement.step(request(1, view(0)))

    # A fresh choice: it runs 50 ticks, or ends at once when it acts on the spot.
    assert member.decide_at in (2, 51)


def stage3(**network):
    base = load_config(REPO_ROOT / "config" / "stage-3.yaml")
    return dataclasses.replace(base, kind="network",
                               network=dataclasses.replace(base.network, rollout_ticks=100, **network))


def view7(npc_id=0, *, reward=0.0, x=10, y=10):
    observation = pb.Observation(surroundings=[E] * 49, view_size=7, x=x, y=y, energy=100, season=1,
                                 season_ticks_left=400)
    return pb.NpcView(npc_id=npc_id, observation=observation, reward=reward)


def test_warm_up_lets_the_scenario_lead_and_keeps_no_experience():
    settlement = NetworkSettlement(stage3(warmup_ticks=3))

    for tick in range(3):
        settlement.step(request(tick, view7(0, reward=1.0), view7(1, x=12, reward=1.0)))

    assert settlement.warmup == 3
    assert settlement.learned_ticks == 0
    assert all(member.pending is None and member.steps == [] for member in settlement.members.values())
    settlement.step(request(3, view7(0), view7(1, x=12)))
    assert settlement.learned_ticks == 1
    assert all(member.pending is not None for member in settlement.members.values())


def test_warm_up_shrinks_with_learning_and_is_off_in_show_mode():
    settlement = NetworkSettlement(stage3(warmup_ticks=1000, warmup_decay_ticks=400))

    assert settlement.warmup_for(0) == 1000
    assert settlement.warmup_for(100) == 750
    assert settlement.warmup_for(400) == 0
    assert NetworkSettlement(stage3(warmup_ticks=1000), learning=False).warmup_for(0) == 0


def test_learned_ticks_survive_saving(tmp_path):
    path = tmp_path / "network.pt"
    trained = NetworkSettlement(stage3(warmup_ticks=2), save_path=path)
    for tick in range(5):
        trained.step(request(tick, view7(0)))
    trained.save()

    loaded = NetworkSettlement(stage3(warmup_ticks=2), save_path=path, load=True)

    assert trained.learned_ticks == 3
    assert loaded.learned_ticks == 3


def test_team_share_mixes_in_the_settlement_mean_reward():
    settlement = NetworkSettlement(stage3(team_share=0.5, skill_ticks=1, gamma=0.9))
    settlement.step(request(0, view7(0), view7(1, x=12)))

    settlement.step(request(1, view7(0, reward=1.0), view7(1, x=12, reward=-3.0)))

    first, second = (settlement.members[i].steps[0].reward for i in (0, 1))
    assert first == pytest.approx(0.5 * 1.0 + 0.5 * -1.0)
    assert second == pytest.approx(0.5 * -3.0 + 0.5 * -1.0)


def test_world_restored_after_the_warm_up_gets_the_settlement_back():
    settlement = NetworkSettlement(stage3(warmup_ticks=2))
    water = [E] * 49
    water[0] = W
    first = pb.Observation(surroundings=water, view_size=7, x=10, y=10, energy=100, season=1)
    settlement.step(request(0, pb.NpcView(npc_id=0, observation=first)))
    settlement.step(request(1, view7(0, x=30)))
    settlement.step(request(2, view7(0, x=30)))
    settlement.step(request(3, view7(0, x=30)))
    settlement.members[0].map.see(view7(0, x=40).observation, 3)   # learned after the warm-up

    settlement.step(request(2, view7(0, x=30)))

    known = settlement.members[0].map
    assert known.places(pb.CELL_WATER) == [(7, 7)]
    assert known.cell((40, 40)) is None
    assert settlement.members[0].pending is not None


def test_world_restored_at_a_tick_the_brain_does_not_know_is_an_error():
    settlement = NetworkSettlement(stage3())
    settlement.step(request(0, view7(0)))
    settlement.step(request(1, view7(0)))

    with pytest.raises(ValueError, match="restored at tick 1"):
        settlement.step(request(1, view7(0)))


def test_skill_ends_once_it_has_done_its_work():
    settlement = NetworkSettlement(stage3(skill_ticks=16))
    water_here = [E] * 49
    water_here[24] = W
    settlement.step(request(0, pb.NpcView(npc_id=0, observation=pb.Observation(
        surroundings=water_here, view_size=7, x=10, y=10, energy=100, thirst=60, season=4))))
    member = settlement.members[0]
    member.choice, member.decide_at = 0, 16          # drink, standing on water
    member.pending = None

    response = settlement.step(request(1, pb.NpcView(npc_id=0, observation=pb.Observation(
        surroundings=water_here, view_size=7, x=10, y=10, energy=100, thirst=60, season=4))))

    assert response.actions[0].action == pb.ACTION_CONSUME
    assert member.decide_at == 2


def test_sitting_in_a_shelter_keeps_the_skill():
    settlement = NetworkSettlement(stage3(skill_ticks=16))
    shelter_here = [E] * 49
    shelter_here[24] = pb.CELL_SHELTER
    obs = pb.Observation(surroundings=shelter_here, view_size=7, x=10, y=10, energy=100, season=4)
    settlement.step(request(0, pb.NpcView(npc_id=0, observation=obs)))
    member = settlement.members[0]
    member.choice, member.decide_at = settlement.choices.names.index("take_shelter"), 16

    response = settlement.step(request(1, pb.NpcView(npc_id=0, observation=obs)))

    assert response.actions[0].action == pb.ACTION_STAY
    assert member.decide_at == 16


def test_network_is_copied_every_few_worlds(tmp_path):
    settlement = NetworkSettlement(stage3(checkpoint_every_episodes=2), save_path=tmp_path / "network.pt")

    for world in range(5):
        for tick in range(3):
            settlement.step(request(tick, view7(0)))

    copies = sorted(path.name for path in (tmp_path / "checkpoints").iterdir())
    assert copies == ["network-000000006.pt", "network-000000012.pt"]


def test_update_stops_once_the_policy_moved_too_far():
    loose = Ppo(view_cells=1, scalars=1, choices=2, seed=1,
                config=NetworkConfig(minibatch=8, epochs=10, learning_rate=0.05))
    tight = Ppo(view_cells=1, scalars=1, choices=2, seed=1,
                config=NetworkConfig(minibatch=8, epochs=10, learning_rate=0.05, target_kl=0.001))
    def pieces():
        return [([Step([1], [0.0], [True, True], arm, -0.69, 0.0, reward=float(arm), done=True)], 0.0)
                for arm in (0, 1) * 32]

    loose.update(pieces())
    tight.update(pieces())

    def preference(ppo):
        logits, _ = ppo.evaluate([[1]], [[0.0]], [[True, True]])
        return torch.softmax(logits, dim=1)[0, 1].item()

    assert tight.stopped_early and not loose.stopped_early
    assert 0.5 < preference(tight) < preference(loose)


def test_building_keeps_the_skill_after_it_acts():
    settlement = NetworkSettlement(stage3(skill_ticks=16))
    site_here = [E] * 49
    site_here[24] = pb.CELL_SITE_SHELTER
    obs = pb.Observation(surroundings=site_here, view_size=7, x=10, y=10, energy=100, season=1, carried_wood=2)
    settlement.step(request(0, pb.NpcView(npc_id=0, observation=obs)))
    member = settlement.members[0]
    member.choice, member.decide_at = settlement.choices.names.index("build_shelter"), 16

    response = settlement.step(request(1, pb.NpcView(npc_id=0, observation=obs)))

    assert response.actions[0].action == pb.ACTION_BUILD_SHELTER
    assert member.decide_at == 16


def test_finished_building_does_not_roll_over_into_a_new_site():
    settlement = NetworkSettlement(stage3(skill_ticks=16))
    obs = pb.Observation(surroundings=[E] * 49, view_size=7, x=10, y=10, energy=100, season=1)
    settlement.step(request(0, pb.NpcView(npc_id=0, observation=obs)))
    member = settlement.members[0]
    member.choice, member.decide_at = settlement.choices.names.index("build_shelter"), 16   # no site left

    settlement.step(request(1, pb.NpcView(npc_id=0, observation=obs)))

    assert member.decide_at != 16          # the NPC chose again instead of starting the next shelter
