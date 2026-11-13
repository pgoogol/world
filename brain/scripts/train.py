"""One training run: starts the brains server, runs the world headless, saves the tables.

Usage (from the brain/ directory, after `.\\mvnw.cmd verify` in world/):
    uv run python scripts/train.py --variant self|fixed|random [--ticks N] [--resume] [--evaluate]
                                   [--config PATH] [--name NAME] [--sharing all|none|inherit|observe|map] [--episodes]
                                   [--settlement]

Writes statistics to out/<variant>.csv and, for learning variants, tables to brain/data/.
Every run starts from empty tables unless --resume is given, so the same seed gives the same CSV.
"""

import argparse
import dataclasses
import os
import subprocess
import sys
from pathlib import Path

from brain.config import BRAIN_DIR, DEFAULT_CONFIG, REPO_ROOT, load_config
from brain.server import build_group, start_server

WORLD_JAR = REPO_ROOT / "world" / "world-app" / "target" / "world-app.jar"
VARIANTS = {
    "random": {"kind": "random"},
    "fixed": {"kind": "qlearning", "tuning": "fixed"},
    "self": {"kind": "qlearning", "tuning": "self"},
    # Stage 3: the hand-written settlement, with and without building.
    "scripted": {"kind": "scripted"},
    "scripted-nobuild": {"kind": "scripted", "build": False},
}


SHARING = {
    "none": {"inherit": False, "observe": False, "map": False},
    "inherit": {"inherit": True, "observe": False, "map": False},
    "observe": {"inherit": False, "observe": True, "map": False},
    "map": {"inherit": False, "observe": False, "map": True},
    "all": {"inherit": True, "observe": True, "map": True},
}


def java_command() -> str:
    """JDK 25 from JAVA_HOME; `java` on PATH may be an older version."""
    java_home = os.environ.get("JAVA_HOME")
    return str(Path(java_home) / "bin" / "java") if java_home else "java"


def main() -> int:
    parser = argparse.ArgumentParser(description="Run one training variant end to end")
    parser.add_argument("--variant", choices=sorted(VARIANTS), required=True)
    parser.add_argument("--ticks", type=int, help="default: training.ticks from the config")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--out", type=Path, help="default: out/<variant>.csv in the repo root")
    parser.add_argument("--resume", action="store_true", help="continue from saved tables instead of empty ones")
    parser.add_argument("--data-dir", type=Path, help="where the tables live (default: brain.data_dir)")
    parser.add_argument("--name", help="name of the output files (default: the variant); use it to keep "
                                       "several runs of one variant apart")
    parser.add_argument("--sharing", choices=sorted(SHARING),
                        help="stage 2: which knowledge sharing mechanisms are on (default: as in the config)")
    parser.add_argument("--settlement", action="store_true",
                        help="stage 3: also write out/<name>-settlement.csv: population, stores and buildings per window")
    parser.add_argument("--episodes", action="store_true",
                        help="also write out/<name>-episodes.csv: one row per life episode, birth and death of old age")
    parser.add_argument("--evaluate", action="store_true",
                        help="measure the saved tables: no learning, minimal epsilon, nothing saved; "
                             "statistics go to out/<variant>-eval.csv")
    args = parser.parse_args()

    if not WORLD_JAR.exists():
        print(f"Missing {WORLD_JAR}. Build the world first: .\\mvnw.cmd verify in world/", file=sys.stderr)
        return 1

    overrides = dict(VARIANTS[args.variant])
    build = overrides.pop("build", None)
    config = dataclasses.replace(load_config(args.config), **overrides)
    if build is not None:
        config = dataclasses.replace(config, scenario=dataclasses.replace(config.scenario, build=build))
    if args.sharing:
        config = dataclasses.replace(config, sharing=dataclasses.replace(config.sharing, **SHARING[args.sharing]))
    name = args.name or args.variant
    # Absolute paths: the world runs with the repo root as its working directory.
    suffix = "-eval" if args.evaluate else ""
    out = (args.out or REPO_ROOT / "out" / f"{name}{suffix}.csv").resolve()
    config_path = args.config.resolve()
    data_dir = args.data_dir.resolve() if args.data_dir else None
    if data_dir is None and args.name:
        # A named run keeps its own tables, so it never overwrites another run.
        data_dir = BRAIN_DIR / config.data_dir / args.name
    if args.evaluate:
        group = build_group(config, mode="show", data_dir=data_dir)
    else:
        group = build_group(config, mode="train", data_dir=data_dir, fresh=not args.resume)
    server, _ = start_server(group, config.address)
    try:
        command = [java_command(), "-jar", str(WORLD_JAR), "--config", str(config_path), "--out", str(out)]
        if args.ticks:
            command += ["--ticks", str(args.ticks)]
        if args.episodes:
            command += ["--episodes", str(out.with_name(out.stem + "-episodes.csv"))]
        if args.settlement:
            command += ["--settlement", str(out.with_name(out.stem + "-settlement.csv"))]
        result = subprocess.run(command, cwd=REPO_ROOT)
    finally:
        server.stop(grace=1).wait()
    if result.returncode == 0 and not args.evaluate:
        group.save()
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
