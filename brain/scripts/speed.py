"""Step 3.7: ticks per second of the stage 3 world, with its time split into world, transport and brain.

Usage (from the brain/ directory, after `.\\mvnw.cmd verify` in world/):
    uv run --group nn python scripts/speed.py [--ticks 2000] [--npcs 20 50]

For every settlement size it runs the same world with four brains:
  local     random moves inside the world process: the world alone
  random    random moves from the Python server: world + gRPC
  scripted  the hand-written settlement of step 3.6
  network   an untrained network (one forward pass per tick for the whole group)
and prints ticks per second, how long a year takes and the time per tick spent in the brain.
Without the `nn` group the network row is skipped.

Random moves and an untrained network kill a settlement within a few hundred ticks, so those rows
run with a constant population (no final death, no old age, no births). The scripted settlement
survives and runs under the real rules.
"""

import argparse
import dataclasses
import os
import re
import subprocess
import sys
import time
from pathlib import Path

import grpc
import yaml

from brain.config import REPO_ROOT, load_config
from brain.proto import brain_pb2_grpc
from brain.server import build_group

WORLD_JAR = REPO_ROOT / "world" / "world-app" / "target" / "world-app.jar"


class TimedService(brain_pb2_grpc.BrainServicer):
    """Answers Step and counts the time spent inside the brain."""

    def __init__(self, group):
        self.group = group
        self.seconds = 0.0
        self.calls = 0

    def Step(self, request, context):
        start = time.perf_counter()
        response = self.group.step(request)
        self.seconds += time.perf_counter() - start
        self.calls += 1
        return response


def java() -> str:
    java_home = os.environ.get("JAVA_HOME")
    return str(Path(java_home) / "bin" / "java") if java_home else "java"


def run_world(config_path: Path, ticks: int, out: Path, local: bool) -> float:
    command = [java(), "-jar", str(WORLD_JAR), "--config", str(config_path), "--ticks", str(ticks), "--out", str(out)]
    if local:
        command += ["--brain", "local"]
    result = subprocess.run(command, cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    ran = int(re.search(r"Ran (\d+) ticks", result.stdout).group(1))
    rate = float(re.search(r"\(([\d.]+) ticks/s\)", result.stdout).group(1))
    if ran < ticks:
        print(f"    (the settlement died out after {ran} ticks)")
    return rate


def run_with_server(group, address: str, config_path: Path, ticks: int, out: Path) -> tuple[float, float]:
    from concurrent import futures

    service = TimedService(group)
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    brain_pb2_grpc.add_BrainServicer_to_server(service, server)
    server.add_insecure_port(address)
    server.start()
    try:
        rate = run_world(config_path, ticks, out, local=False)
    finally:
        server.stop(grace=1).wait()
    return rate, 1000 * service.seconds / max(1, service.calls)


def main() -> int:
    parser = argparse.ArgumentParser(description="Speed of the stage 3 world")
    parser.add_argument("--ticks", type=int, default=2000)
    parser.add_argument("--npcs", type=int, nargs="+", default=[20, 50])
    parser.add_argument("--config", type=Path, default=REPO_ROOT / "config" / "stage-3.yaml")
    parser.add_argument("--out-dir", type=Path, default=REPO_ROOT / "out" / "speed")
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)

    with open(args.config, encoding="utf-8") as file:
        root = yaml.safe_load(file)
    year_ticks = 4 * int(root["seasons"]["season_ticks"])
    try:
        from brain.network_probe import NetworkProbe
    except ImportError:
        NetworkProbe = None

    print(f"{'npcs':>4}  {'brain':<9}  {'ticks/s':>8}  {'s/year':>7}  {'ms/tick':>7}  {'brain ms/tick':>13}")
    for npcs in args.npcs:
        root["world"]["npcs"] = npcs
        root["population"]["max_npcs"] = max(npcs, root["population"]["max_npcs"])
        config_path = args.out_dir / f"stage-3-{npcs}.yaml"
        config_path.write_text(yaml.safe_dump(root, sort_keys=False), encoding="utf-8")
        config = load_config(config_path)
        constant = yaml.safe_load(yaml.safe_dump(root))
        constant["population"].update(death_is_final=False, birth_food_cost=0)
        constant["world"]["lifespan_ticks"] = 0
        constant_path = args.out_dir / f"stage-3-{npcs}-constant.yaml"
        constant_path.write_text(yaml.safe_dump(constant, sort_keys=False), encoding="utf-8")

        def report(name, rate, brain_ms=None):
            brain_text = "-" if brain_ms is None else f"{brain_ms:.2f}"
            print(f"{npcs:4d}  {name:<9}  {rate:8.0f}  {year_ticks / rate:7.1f}  {1000 / rate:7.2f}  {brain_text:>13}")

        report("local", run_world(constant_path, args.ticks, args.out_dir / "local.csv", local=True))
        for name, group, path in [
            ("random", build_group(dataclasses.replace(config, kind="random")), constant_path),
            ("scripted", build_group(config), config_path),
            ("network", NetworkProbe(config.seed) if NetworkProbe else None, constant_path),
        ]:
            if group is None:
                print(f"{npcs:4d}  {name:<9}  skipped: install the nn group (uv sync --group nn)")
                continue
            rate, brain_ms = run_with_server(group, config.address, path, args.ticks, args.out_dir / f"{name}.csv")
            report(name, rate, brain_ms)
    return 0


if __name__ == "__main__":
    sys.exit(main())
