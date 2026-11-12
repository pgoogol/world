"""Smoke tests for the code generated from proto/brain.proto."""

import pytest

try:
    from brain.proto import brain_pb2, brain_pb2_grpc
except ImportError:  # pragma: no cover - depends on the local checkout
    pytest.fail(
        "Generated proto code is missing. Run `uv run python scripts/gen_proto.py` in brain/ first.",
        pytrace=False,
    )


def test_step_request_survives_serialization():
    observation = brain_pb2.Observation(
        surroundings=[brain_pb2.CELL_LAVA], hunger=10, thirst=20, energy=100
    )
    request = brain_pb2.StepRequest(
        tick=7,
        npcs=[brain_pb2.NpcView(npc_id=3, observation=observation, reward=0.1)],
    )

    parsed = brain_pb2.StepRequest.FromString(request.SerializeToString())

    assert parsed == request
    assert not parsed.npcs[0].HasField("final_observation")


def test_unset_enum_reads_as_unspecified():
    assert brain_pb2.NpcAction().action == brain_pb2.ACTION_UNSPECIFIED
    assert brain_pb2.Observation(surroundings=[0]).surroundings[0] == brain_pb2.CELL_UNSPECIFIED


def test_brain_service_exposes_step():
    assert hasattr(brain_pb2_grpc, "BrainStub")
    assert hasattr(brain_pb2_grpc.BrainServicer, "Step")


def test_stage_three_fields_survive_serialization():
    observation = brain_pb2.Observation(
        surroundings=[brain_pb2.CELL_TREE] * 49,
        view_size=7,
        carried_food=2,
        carried_wood=1,
        season=4,
        season_ticks_left=120,
        here_amount=37,
    )
    request = brain_pb2.StepRequest(
        tick=3, settlement_id=0, npcs=[brain_pb2.NpcView(npc_id=1, observation=observation)]
    )

    parsed = brain_pb2.StepRequest.FromString(request.SerializeToString())

    assert parsed == request
    assert parsed.npcs[0].observation.view_size == 7
    assert brain_pb2.ACTION_WITHDRAW == 13
    assert brain_pb2.CELL_STORE == 13


def test_stage_three_config_loads_on_the_brain_side():
    from brain.config import REPO_ROOT, load_config

    config = load_config(REPO_ROOT / "config" / "stage-3.yaml")

    assert config.seed == 42
    assert config.kind == "scripted"
