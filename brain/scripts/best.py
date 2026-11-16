"""Step 3.11: measures every saved copy of a settlement network over five years and names the best.

Usage (from the brain/ directory):
    uv run --group nn python scripts/best.py --name s3-l3r [--port 50051] [--set section.key=value ...]

Reads brain/data/<name>/checkpoints/network-*.pt (written with brain.network.checkpoint_every_episodes),
runs each one on the target configuration without learning and without warm-up, and prints how many
winters the settlement got through. The best copy goes to brain/data/<name>-best/network.pt.
"""

import argparse
import shutil
import subprocess
import sys

from brain.config import BRAIN_DIR, REPO_ROOT
from brain.winters import died_out_at, read_rows, survives, winters

FOUNDERS = 20
SEASON_TICKS = 500


def main() -> int:
    parser = argparse.ArgumentParser(description="Measure every saved copy of a settlement network")
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, default=50051)
    parser.add_argument("--set", action="append", default=[], dest="overrides")
    args = parser.parse_args()

    copies = sorted((BRAIN_DIR / "data" / args.name / "checkpoints").glob("network-*.pt"))
    if not copies:
        print(f"No saved copies in brain/data/{args.name}/checkpoints", file=sys.stderr)
        return 1
    scratch = BRAIN_DIR / "data" / f"{args.name}-measure"
    results = []
    print(f"{'learned ticks':>13}  {'winters':>7}  {'died at':>7}  {'population':>10}  {'5 winters':>9}")
    for copy in copies:
        scratch.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(copy, scratch / "network.pt")
        run = f"{args.name}-measure"
        command = [sys.executable, "scripts/train.py", "--variant", "network", "--config", "../config/stage-3.yaml",
                   "--name", run, "--data-dir", str(scratch), "--evaluate", "--settlement",
                   "--set", f"brain.address=localhost:{args.port}"]
        for override in args.overrides:
            command += ["--set", override]
        subprocess.run(command, cwd=BRAIN_DIR, check=True, capture_output=True)
        rows = read_rows(REPO_ROOT / "out" / f"{run}-eval-settlement.csv")
        winter_list = winters(rows, SEASON_TICKS)
        survived = sum(1 for winter in winter_list if winter.survived)
        died = died_out_at(rows)
        ok = survives(winter_list, 5, FOUNDERS, rows[-1].population)
        learned = int(copy.stem.split("-")[1])
        results.append((ok, survived, died is None, rows[-1].population, learned, copy))
        print(f"{learned:13d}  {survived:7d}  {died if died is not None else '-':>7}  {rows[-1].population:10d}  "
              f"{'yes' if ok else 'no':>9}", flush=True)

    best = max(results, key=lambda result: result[:4])
    target = BRAIN_DIR / "data" / f"{args.name}-best"
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(best[5], target / "network.pt")
    print(f"best: {best[5].name}, {best[1]} winters survived; copied to brain/data/{args.name}-best/network.pt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
