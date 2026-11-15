"""Stage 3: prints the learning curve of runs trained in episodes.

Usage (from the brain/ directory): uv run python scripts/curve.py --runs s3-network [s3-network-nobuild] [--blocks 10]

Reads out/<run>-curve.csv (written by train.py --episode-ticks). Every row is the mean over a block of
episodes: how long settlements lasted, how many winters they saw and how much they sowed, built and stored.
"""

import argparse
import sys
from pathlib import Path

from brain.config import REPO_ROOT
from brain.curve import blocks, read_episodes


def main() -> int:
    parser = argparse.ArgumentParser(description="Learning curve of settlements trained in episodes")
    parser.add_argument("--runs", nargs="+", required=True)
    parser.add_argument("--blocks", type=int, default=10)
    parser.add_argument("--out-dir", type=Path, default=REPO_ROOT / "out")
    args = parser.parse_args()

    for run in args.runs:
        file = args.out_dir / f"{run}-curve.csv"
        if not file.exists():
            print(f"{run}: no curve yet ({file.name} missing)", file=sys.stderr)
            return 1
        episodes = read_episodes(file)
        print(f"{run}: {len(episodes)} episodes")
        print(f"  {'episodes':>11}  {'to tick':>9}  {'ticks':>7}  {'winters':>7}  {'sown':>6}  {'harvested':>9}  "
              f"{'shelters':>8}  {'stores':>6}  {'stock at winter':>15}  {'births':>6}")
        for block in blocks(episodes, args.blocks):
            m = block.means
            span = f"{block.first_episode}-{block.first_episode + block.episodes - 1}"
            print(f"  {span:>11}  {block.end_tick:9d}  {m['ticks']:7.0f}  {m['winters']:7.2f}  {m['fields_sown']:6.1f}  "
                  f"{m['fields_harvested']:9.1f}  {m['shelters_built']:8.2f}  {m['stores_built']:6.2f}  "
                  f"{m['stored_food_winter']:15.1f}  {m['births']:6.2f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
