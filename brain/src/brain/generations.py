"""Reads the episode log written by the world and measures how fast each generation learns."""

import csv
import statistics
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class NpcLife:
    npc_id: int
    generation: int
    # (age in ticks since birth when the episode ended, episode length), in order.
    episodes: list[tuple[int, int]] = field(default_factory=list)


@dataclass(frozen=True)
class GenerationReport:
    generation: int
    npcs: int
    reached_level: int               # how many NPCs got to the adult level at all
    median_age_at_level: float | None  # among all NPCs; None when fewer than half reached it
    adult_mean_life: float | None    # mean episode length of episodes ended at adult age


def read_lives(path: Path) -> dict[int, NpcLife]:
    """All NPCs that had at least one finished episode, by id."""
    lives: dict[int, NpcLife] = {}
    with open(path, newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            if row["event"] != "episode":
                continue
            npc_id = int(row["npc_id"])
            life = lives.setdefault(npc_id, NpcLife(npc_id, int(row["generation"])))
            life.episodes.append((int(row["npc_age"]), int(row["value"])))
    return lives


def age_at_level(life: NpcLife, level: float, rolling: int) -> int | None:
    """The NPC's age when the mean length of its last `rolling` episodes first reached the level."""
    lengths = [length for _, length in life.episodes]
    for end in range(rolling, len(lengths) + 1):
        if sum(lengths[end - rolling:end]) / rolling >= level:
            return life.episodes[end - 1][0]
    return None


def median_age_at_level(lives: list[NpcLife], level: float, rolling: int) -> float | None:
    """Median over all given NPCs, counting those that never reached the level as slower than any that did.

    Returns None when the median NPC never reached the level.
    """
    ages = sorted(
        (age for life in lives if (age := age_at_level(life, level, rolling)) is not None)
    )
    if not lives or len(ages) * 2 <= len(lives):
        return None
    padded = ages + [float("inf")] * (len(lives) - len(ages))
    median = statistics.median_low(padded)
    return None if median == float("inf") else float(median)


def report(lives: dict[int, NpcLife], level: float, rolling: int, adult_age: int) -> list[GenerationReport]:
    by_generation: dict[int, list[NpcLife]] = {}
    for life in lives.values():
        by_generation.setdefault(life.generation, []).append(life)

    reports = []
    for generation in sorted(by_generation):
        members = by_generation[generation]
        adult_lengths = [length for life in members for age, length in life.episodes if age >= adult_age]
        reports.append(GenerationReport(
            generation=generation,
            npcs=len(members),
            reached_level=sum(1 for life in members if age_at_level(life, level, rolling) is not None),
            median_age_at_level=median_age_at_level(members, level, rolling),
            adult_mean_life=statistics.fmean(adult_lengths) if adult_lengths else None,
        ))
    return reports
