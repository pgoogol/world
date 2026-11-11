"""One training run: starts the brains server, runs the world headless, saves the tables.

Usage (from the brain/ directory, after `.\\mvnw.cmd verify` in world/):
    uv run python scripts/train.py --variant self|fixed|random [--ticks N] [--resume] [--evaluate]

Writes statistics to out/<variant>.csv and, for learning variants, tables to brain/data/.
Every run starts from empty tables unless --resume is given, so the same seed gives the same CSV.
"""

import argparse
import dataclasses
import os
import subprocess
import sys
from pathlib import Path

from brain.config import DEFAULT_CONFIG, REPO_ROOT, load_config
from brain.server import build_group, start_server

WORLD_JAR = REPO_ROOT / "world" / "world-app" / "target" / "world-app.jar"
VARIANTS = {
    "random": {"kind": "random"},
    "fixed": {"kind": "qlearning", "tuning": "fixed"},
    "self": {"kind": "qlearning", "tuning": "self"},
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
    parser.add_argument("--evaluate", action="store_true",
                        help="measure the saved tables: no learning, minimal epsilon, nothing saved; "
                             "statistics go to out/<variant>-eval.csv")
    args = parser.parse_args()

    if not WORLD_JAR.exists():
        print(f"Missing {WORLD_JAR}. Build the world first: .\\mvnw.cmd verify in world/", file=sys.stderr)
        return 1

    config = dataclasses.replace(load_config(args.config), **VARIANTS[args.variant])
    # Absolute paths: the world runs with the repo root as its working directory.
    suffix = "-eval" if args.evaluate else ""
    out = (args.out or REPO_ROOT / "out" / f"{args.variant}{suffix}.csv").resolve()
    config_path = args.config.resolve()
    if args.evaluate:
        group = build_group(config, mode="show")
    else:
        group = build_group(config, mode="train", fresh=not args.resume)
    server, _ = start_server(group, config.address)
    try:
        command = [java_command(), "-jar", str(WORLD_JAR), "--config", str(config_path), "--out", str(out)]
        if args.ticks:
            command += ["--ticks", str(args.ticks)]
        result = subprocess.run(command, cwd=REPO_ROOT)
    finally:
        server.stop(grace=1).wait()
    if result.returncode == 0 and not args.evaluate:
        group.save()
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
