"""Stage 2 report: how each generation of a settlement does, per run.

Usage (from the brain/ directory):
    uv run python scripts/generations.py --baseline s2-random --runs s2-all s2-none [...]

Reads out/<run>-episodes.csv for every run and out/<baseline>.csv for the random brain.
The adult level is 5x the mean life length of the random brain in the same world.
An NPC reaches the level when the mean of its last 5 episodes gets there.
Adult mean life counts episodes that ended in the second half of the configured lifespan.
"""

import argparse
import sys
from pathlib import Path

import yaml

from brain.config import REPO_ROOT
from brain.generations import read_lives, report
from brain.summary import mean_life, read_windows

LEVEL_RATIO = 5.0
ROLLING_EPISODES = 5


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare generations across runs")
    parser.add_argument("--baseline", required=True, help="name of the random brain run")
    parser.add_argument("--runs", nargs="+", required=True, help="names of the runs to report")
    parser.add_argument("--config", type=Path, default=REPO_ROOT / "config" / "stage-2.yaml")
    parser.add_argument("--out-dir", type=Path, default=REPO_ROOT / "out")
    args = parser.parse_args()

    with open(args.config, encoding="utf-8") as file:
        lifespan = int(yaml.safe_load(file)["world"]["lifespan_ticks"])
    adult_age = lifespan // 2

    baseline = mean_life(read_windows(args.out_dir / f"{args.baseline}.csv"))
    level = LEVEL_RATIO * baseline
    print(f"random brain: mean life {baseline:.1f} ticks; adult level = {LEVEL_RATIO:.0f}x = {level:.1f} ticks")
    print(f"adult age: from {adult_age} ticks (half of the lifespan)\n")

    for run in args.runs:
        file = args.out_dir / f"{run}-episodes.csv"
        if not file.exists():
            print(f"{run}: no run yet ({file.name} missing)\n")
            continue
        print(run)
        print("  gen  npcs  reached level  median age at level  adult mean life")
        for row in report(read_lives(file), level, ROLLING_EPISODES, adult_age):
            median = f"{row.median_age_at_level:.0f}" if row.median_age_at_level is not None else "never"
            adult = f"{row.adult_mean_life:.0f}" if row.adult_mean_life is not None else "-"
            print(f"  {row.generation:3d}  {row.npcs:4d}  {row.reached_level:7d}/{row.npcs:<5d}  {median:>19s}  {adult:>15s}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
