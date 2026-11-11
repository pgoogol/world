"""The server answers Brain.Step with one valid action per NPC."""

import grpc
import pytest

from brain.brains import ACTIONS, RandomBrain, npc_rng
from brain.config import load_config
from brain.group import BrainGroup
from brain.proto import brain_pb2, brain_pb2_grpc
from brain.server import start_server


def random_group(seed: int = 42) -> BrainGroup:
    return BrainGroup(lambda npc_id: RandomBrain(npc_rng(seed, npc_id)))


def request(tick: int, npc_ids=range(5)) -> brain_pb2.StepRequest:
    observation = brain_pb2.Observation(surroundings=[brain_pb2.CELL_EMPTY] * 9, energy=100)
    return brain_pb2.StepRequest(
        tick=tick, npcs=[brain_pb2.NpcView(npc_id=i, observation=observation) for i in npc_ids]
    )


@pytest.fixture
def stub():
    server, port = start_server(random_group(), "localhost:0")
    with grpc.insecure_channel(f"localhost:{port}") as channel:
        yield brain_pb2_grpc.BrainStub(channel)
    server.stop(grace=None)


def test_step_returns_one_valid_action_per_npc(stub):
    response = stub.Step(request(tick=0))

    assert [action.npc_id for action in response.actions] == [0, 1, 2, 3, 4]
    assert all(action.action in ACTIONS for action in response.actions)


def test_same_seed_gives_the_same_actions():
    first, second = random_group(seed=7), random_group(seed=7)

    for tick in range(50):
        assert first.step(request(tick)) == second.step(request(tick))


def test_npc_actions_do_not_depend_on_who_else_is_in_the_request():
    alone, together = random_group(), random_group()

    for tick in range(50):
        action_alone = alone.step(request(tick, npc_ids=[3])).actions[0]
        action_together = together.step(request(tick)).actions[3]
        assert action_alone == action_together


def test_stage_config_loads():
    config = load_config()

    assert config.seed == 42
    assert config.address == "localhost:50051"
    assert config.need_levels == (50, 80)
    assert config.gamma == 0.95
