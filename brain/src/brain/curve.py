"""Stage 3: the learning curve of a settlement trained in episodes, read from out/<name>-curve.csv."""

import csv
from dataclasses import dataclass
from pathlib import Path

COLUMNS = ("ticks", "winters", "fields_sown", "fields_harvested", "shelters_built", "stores_built",
           "stored_food_winter", "births")


@dataclass(frozen=True)
class Block:
    """Means over a block of consecutive episodes."""

    first_episode: int
    episodes: int
    end_tick: int
    means: dict[str, float]


def read_episodes(path: Path) -> list[dict[str, int]]:
    with open(path, newline="", encoding="utf-8") as file:
        return [{key: int(value) for key, value in row.items()} for row in csv.DictReader(file)]


def blocks(episodes: list[dict[str, int]], count: int) -> list[Block]:
    """Splits the run into `count` blocks of equal numbers of episodes (the last one takes the rest)."""
    if not episodes:
        return []
    count = min(count, len(episodes))
    size = len(episodes) // count
    result = []
    for index in range(count):
        chunk = episodes[index * size:] if index == count - 1 else episodes[index * size:(index + 1) * size]
        result.append(Block(
            first_episode=chunk[0]["episode"], episodes=len(chunk), end_tick=chunk[-1]["end_tick"],
            means={column: sum(row[column] for row in chunk) / len(chunk) for column in COLUMNS},
        ))
    return result
