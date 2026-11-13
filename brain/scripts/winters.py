"""Stage 3 report: how settlements got through their winters.

Usage (from the brain/ directory):
    uv run python scripts/winters.py --runs s3-scripted s3-scripted-nobuild [--winters 5]

Reads out/<run>-settlement.csv. A winter counts as survived when at least half of the settlement
sees the spring; a settlement survives N winters when every one of the first N counts and it ends
the N-th year no smaller than it started.
"""

import argparse
import sys
from pathlib import Path

import yaml

from brain.config import REPO_ROOT
from brain.winters import died_out_at, read_rows, survives, winters


def main() -> int:
    parser = argparse.ArgumentParser(description="Winters of stage 3 settlements")
    parser.add_argument("--runs", nargs="+", required=True)
    parser.add_argument("--winters", type=int, default=5)
    parser.add_argument("--config", type=Path, default=REPO_ROOT / "config" / "stage-3.yaml")
    parser.add_argument("--out-dir", type=Path, default=REPO_ROOT / "out")
    args = parser.parse_args()

    with open(args.config, encoding="utf-8") as file:
        root = yaml.safe_load(file)
    season_ticks = int(root["seasons"]["season_ticks"])
    founders = int(root["world"]["npcs"])
    year_ticks = 4 * season_ticks

    for run in args.runs:
        file = args.out_dir / f"{run}-settlement.csv"
        if not file.exists():
            print(f"{run}: no run yet ({file.name} missing)\n")
            continue
        rows = read_rows(file)
        print(run)
        print("  winter  before  after  stored food  shelters  stores")
        winter_list = winters(rows, season_ticks)
        for winter in winter_list:
            after = "-" if winter.after is None else str(winter.after)
            print(f"  {winter.year:6d}  {winter.before:6d}  {after:>5s}  {winter.stored_food:11d}  "
                  f"{winter.shelters:8d}  {winter.stores:6d}")
        end = next((row for row in rows if row.tick == args.winters * year_ticks), None)
        final_population = end.population if end is not None else rows[-1].population
        extinct = died_out_at(rows)
        if extinct is not None:
            print(f"  died out at tick {extinct} (year {extinct // year_ticks + 1}, "
                  f"end of winter 2 is tick {2 * year_ticks})")
        verdict = survives(winter_list, args.winters, founders, final_population)
        print(f"  survives {args.winters} winters: {'yes' if verdict else 'no'} "
              f"(founders {founders}, population after year {args.winters}: {final_population})\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
