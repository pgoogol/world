"""Saving and loading Q-tables."""

import dataclasses

from brain.brains import QLearningBrain, npc_rng
from brain.config import load_config
from brain.proto import brain_pb2
from brain.server import build_group, tables_path
from brain.storage import load_brains, save_brains
from helpers import FOOD, NEED_LEVELS, fixed, observation, rng, view


def trained_brain(seed=1) -> QLearningBrain:
    npc = QLearningBrain(rng(seed), 0.95, NEED_LEVELS, fixed(epsilon_start=1.0, epsilon_end=0.1, decay_ticks=50))
    for i in range(100):
        npc.act(view(observation(centre=FOOD if i % 2 else brain_pb2.CELL_EMPTY, hunger=i), reward=i % 5))
    return npc


def test_saved_tables_come_back_unchanged(tmp_path):
    path = tmp_path / "tables.pkl"
    brains = {0: trained_brain(1), 3: trained_brain(2)}

    save_brains(path, brains)
    loaded = load_brains(path)

    assert set(loaded) == {0, 3}
    assert loaded[0]["q"] == brains[0].q
    assert loaded[3]["q"] == brains[3].q
    assert loaded[0]["steps"] == 100


def test_loaded_brain_continues_where_the_saved_one_stopped(tmp_path):
    path = tmp_path / "tables.pkl"
    original = trained_brain()
    save_brains(path, {0: original})

    restored = QLearningBrain(rng(9), 0.95, NEED_LEVELS, fixed())
    restored.import_state(load_brains(path)[0])

    assert restored.q == original.q
    assert restored.steps == original.steps


def test_missing_file_means_nothing_saved_yet(tmp_path):
    assert load_brains(tmp_path / "nothing.pkl") == {}


def test_saving_leaves_no_temporary_file(tmp_path):
    save_brains(tmp_path / "tables.pkl", {0: trained_brain()})

    assert [file.name for file in tmp_path.iterdir()] == ["tables.pkl"]


def qlearning_config():
    return dataclasses.replace(load_config(), kind="qlearning", tuning="fixed", save_every_ticks=10)


def step_request(tick: int) -> brain_pb2.StepRequest:
    return brain_pb2.StepRequest(tick=tick, npcs=[view(npc_id=0), view(npc_id=1)])


def test_group_saves_every_configured_number_of_ticks(tmp_path):
    config = qlearning_config()
    group = build_group(config, data_dir=tmp_path)
    path = tables_path(config, tmp_path)

    for tick in range(10):
        group.step(step_request(tick))
    assert not path.exists()

    group.step(step_request(10))
    assert set(load_brains(path)) == {0, 1}


def test_server_start_loads_saved_tables_unless_fresh(tmp_path):
    config = qlearning_config()
    first = build_group(config, data_dir=tmp_path)
    for tick in range(11):
        first.step(step_request(tick))

    resumed = build_group(config, data_dir=tmp_path)
    resumed.step(step_request(0))
    fresh = build_group(config, data_dir=tmp_path, fresh=True)
    fresh.step(step_request(0))

    assert resumed.brains[0].steps == 11 + 1
    assert fresh.brains[0].steps == 1


def test_show_mode_never_writes_tables(tmp_path):
    config = qlearning_config()
    group = build_group(config, mode="show", data_dir=tmp_path)

    for tick in range(25):
        group.step(step_request(tick))
    group.save()

    assert list(tmp_path.iterdir()) == []


def test_npc_streams_differ():
    assert npc_rng(42, 0).random() != npc_rng(42, 1).random()
