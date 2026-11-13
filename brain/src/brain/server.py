"""gRPC server for the brains.

Usage (from the brain/ directory):
    uv run python -m brain.server [--config PATH] [--kind random|qlearning] [--tuning self|fixed]
                                  [--mode train|show] [--data-dir DIR] [--fresh]
"""

import argparse
import dataclasses
from concurrent import futures
from pathlib import Path

import grpc

from brain.brains import QLearningBrain, RandomBrain, npc_rng
from brain.config import BRAIN_DIR, DEFAULT_CONFIG, BrainConfig, load_config
from brain.discovery import DiscoveryMap
from brain.scripted import ScriptedSettlement
from brain.group import BrainGroup
from brain.proto import brain_pb2_grpc
from brain.storage import load_brains
from brain.tuning import FixedSchedule, SelfTuning


class BrainService(brain_pb2_grpc.BrainServicer):
    def __init__(self, group: BrainGroup):
        self._group = group

    def Step(self, request, context):
        return self._group.step(request)


def tables_path(config: BrainConfig, data_dir: Path | None = None) -> Path:
    """Each tuning variant keeps its own file, so comparison runs do not overwrite each other."""
    directory = data_dir if data_dir is not None else BRAIN_DIR / config.data_dir
    return directory / f"qtables-{config.tuning}.pkl"


def build_tuning(config: BrainConfig):
    if config.tuning == "fixed":
        return FixedSchedule(config.fixed)
    if config.tuning == "self":
        return SelfTuning(config.self_tuning)
    raise ValueError(f"Unknown tuning variant: {config.tuning!r}")


def build_group(config: BrainConfig, mode: str = "train", data_dir: Path | None = None, fresh: bool = False) -> BrainGroup:
    """`mode="show"` loads what was learned and neither learns nor saves. `fresh` ignores saved tables."""
    if config.kind == "random":
        return BrainGroup(lambda npc_id: RandomBrain(npc_rng(config.seed, npc_id)))
    if config.kind == "scripted":
        if config.scenario is None:
            raise ValueError("kind 'scripted' needs a brain.scripted section in the config")
        return ScriptedSettlement(config.scenario)
    if config.kind != "qlearning":
        raise ValueError(f"Unknown brain kind: {config.kind!r}")

    path = tables_path(config, data_dir)
    saved = {} if fresh else load_brains(path)
    learning = mode == "train"
    # A settlement that went through generations saved its last living NPCs under high ids, while
    # a new world numbers its founders from zero. Founders without a saved state of their own take
    # over the saved ones in id order, so a new run starts with the knowledge the last one ended with.
    unclaimed = sorted(saved)

    def create(npc_id: int) -> QLearningBrain:
        sharing = config.sharing
        discovery = DiscoveryMap(sharing.map_food_retry_ticks) if sharing.map else None
        brain = QLearningBrain(
            npc_rng(config.seed, npc_id), config.gamma, config.need_levels, build_tuning(config),
            learning=learning, discovery=discovery,
        )
        source = npc_id if npc_id in unclaimed else (unclaimed[0] if unclaimed else None)
        if source is not None:
            unclaimed.remove(source)
            brain.import_state(saved[source])
        return brain

    if learning:
        return BrainGroup(create, save_path=path, save_every_ticks=config.save_every_ticks, sharing=config.sharing)
    return BrainGroup(create, sharing=config.sharing)


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
    parser.add_argument("--kind", choices=["random", "qlearning", "scripted"], help="overrides brain.kind from the config")
    parser.add_argument("--tuning", choices=["self", "fixed"], help="overrides brain.tuning from the config")
    parser.add_argument("--mode", choices=["train", "show"], default="train",
                        help="show: load saved tables, minimal epsilon, no learning and no saving")
    parser.add_argument("--data-dir", type=Path, help="where the Q-tables live (default: brain.data_dir)")
    parser.add_argument("--fresh", action="store_true", help="start from empty tables even if a saved file exists")
    args = parser.parse_args()

    config = load_config(args.config)
    overrides = {name: value for name in ("kind", "tuning") if (value := getattr(args, name))}
    config = dataclasses.replace(config, **overrides)

    group = build_group(config, mode=args.mode, data_dir=args.data_dir, fresh=args.fresh)
    server, port = start_server(group, config.address)
    print(f"Brains server ({config.kind}, {config.tuning}, {args.mode}) listening on port {port}. "
          "Ctrl+C stops it.", flush=True)
    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        server.stop(grace=1).wait()
        if args.mode == "train":
            group.save()


if __name__ == "__main__":
    main()
