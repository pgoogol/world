"""Compares the training runs in out/: random brain, fixed schedule and self-tuning.

Usage (from the brain/ directory): uv run python scripts/summarize.py [--out-dir DIR]

"Trained" life length is the mean over the lives that ended in the last 50 windows of a run.
The threshold tick is the first tick at which a 20-window rolling mean reaches 5x the random brain.
"""

import argparse
import sys
from pathlib import Path

from brain.config import REPO_ROOT
from brain.summary import first_tick_reaching, mean_life, read_windows, trained_mean_life

REQUIRED_RATIO = 5.0
LAST_WINDOWS = 50
ROLLING_WINDOWS = 20


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare training runs")
    parser.add_argument("--out-dir", type=Path, default=REPO_ROOT / "out")
    args = parser.parse_args()

    random_file = args.out_dir / "random.csv"
    if not random_file.exists():
        print(f"Missing {random_file}. Run scripts/train.py --variant random first.", file=sys.stderr)
        return 1
    baseline = mean_life(read_windows(random_file))
    threshold = REQUIRED_RATIO * baseline
    print(f"random   mean life {baseline:8.1f} ticks   (threshold {REQUIRED_RATIO:.0f}x = {threshold:.1f})")

    for variant in ("fixed", "self"):
        file = args.out_dir / f"{variant}.csv"
        if not file.exists():
            print(f"{variant:8s} no run yet ({file.name} missing)")
            continue
        windows = read_windows(file)
        trained = trained_mean_life(windows, LAST_WINDOWS)
        reached = first_tick_reaching(windows, threshold, ROLLING_WINDOWS)
        reached_text = f"reached at tick {reached}" if reached is not None else "never reached"
        print(f"{variant:8s} mean life {trained:8.1f} ticks   {trained / baseline:5.1f}x random   threshold {reached_text}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
