"""gRPC server for the brains.

Usage (from the brain/ directory):
    uv run python -m brain.server [--config PATH] [--kind random|qlearning]
"""

import argparse
from concurrent import futures
from pathlib import Path

import grpc

from brain.brains import RandomBrain, npc_rng
from brain.config import DEFAULT_CONFIG, BrainConfig, load_config
from brain.group import BrainGroup
from brain.proto import brain_pb2_grpc


class BrainService(brain_pb2_grpc.BrainServicer):
    def __init__(self, group: BrainGroup):
        self._group = group

    def Step(self, request, context):
        return self._group.step(request)


def build_group(config: BrainConfig) -> BrainGroup:
    if config.kind == "random":
        return BrainGroup(lambda npc_id: RandomBrain(npc_rng(config.seed, npc_id)))
    raise ValueError(f"Unknown brain kind: {config.kind!r}")


def start_server(group: BrainGroup, address: str) -> tuple[grpc.Server, int]:
    """Starts the server and returns it with the bound port (useful when the address ends in :0)."""
    # One worker: requests are handled in order, which keeps runs reproducible.
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    brain_pb2_grpc.add_BrainServicer_to_server(BrainService(group), server)
    port = server.add_insecure_port(address)
    server.start()
    return server, port


def main() -> None:
    parser = argparse.ArgumentParser(description="NPC brains server")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--kind", choices=["random", "qlearning"], help="overrides brain.kind from the config")
    args = parser.parse_args()

    config = load_config(args.config)
    if args.kind:
        config = config.__class__(**{**config.__dict__, "kind": args.kind})

    server, port = start_server(build_group(config), config.address)
    print(f"Brains server ({config.kind}) listening on port {port}. Ctrl+C stops it.", flush=True)
    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        server.stop(grace=1).wait()


if __name__ == "__main__":
    main()
