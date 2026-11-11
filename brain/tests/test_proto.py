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
