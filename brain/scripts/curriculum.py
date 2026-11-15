"""Step 3.11: trains the settlement network in stages, each with a shorter warm-up.

Usage (from the brain/ directory):
    uv run --group nn python scripts/curriculum.py --name s3-h [--warmups 1400 1000 500 0] [--ticks 400000]
                                                   [--port 50051] [--set section.key=value ...]

Every stage is one train.py run in episodes of 4000 ticks. In stage k the scenario of step 3.10 leads
the settlement up to tick warmups[k], then the network takes over; the network carries over from
stage to stage in brain/data/<name>/. Curves land in out/<name>-w<warmup>-curve.csv.
"""

import argparse
import subprocess
import sys

from brain.config import BRAIN_DIR


def main() -> int:
    parser = argparse.ArgumentParser(description="Train the settlement network with a shrinking warm-up")
    parser.add_argument("--name", required=True)
    parser.add_argument("--warmups", type=int, nargs="+", default=[1400, 1000, 500, 0])
    parser.add_argument("--ticks", type=int, default=400_000, help="ticks of every stage")
    parser.add_argument("--episode-ticks", type=int, default=4000)
    parser.add_argument("--port", type=int, default=50051)
    parser.add_argument("--resume", action="store_true", help="the first stage continues the saved network too")
    parser.add_argument("--set", action="append", default=[], dest="overrides")
    args = parser.parse_args()

    data_dir = BRAIN_DIR / "data" / args.name
    for index, warmup in enumerate(args.warmups):
        command = [sys.executable, "scripts/train.py", "--variant", "network", "--config", "../config/stage-3.yaml",
                   "--name", f"{args.name}-w{warmup}", "--data-dir", str(data_dir), "--ticks", str(args.ticks),
                   "--episode-ticks", str(args.episode_ticks),
                   "--set", f"brain.address=localhost:{args.port}",
                   "--set", f"brain.network.warmup_ticks={warmup}"]
        for override in args.overrides:
            command += ["--set", override]
        if index > 0 or args.resume:
            command.append("--resume")
        print(f"stage {index + 1}/{len(args.warmups)}: warm-up {warmup} ticks", flush=True)
        result = subprocess.run(command, cwd=BRAIN_DIR)
        if result.returncode != 0:
            return result.returncode
    return 0


if __name__ == "__main__":
    sys.exit(main())
