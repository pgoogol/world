"""Stage 3: how a settlement got through its winters, read from the settlement statistics."""

import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SettlementRow:
    tick: int
    population: int
    births: int
    deaths: int
    old_age: int
    stored_food: int
    shelters: int
    stores: int
    fields_sown: int
    fields_ripe: int


@dataclass(frozen=True)
class Winter:
    year: int
    before: int            # population on the first day of winter
    after: int | None      # population on the first day of spring; None when the run ended before it
    stored_food: int       # food in the stores on the first day of winter
    shelters: int
    stores: int

    @property
    def survived(self) -> bool:
        """At least half of the settlement saw the spring."""
        return self.after is not None and self.after > 0 and 2 * self.after >= self.before


def read_rows(path: Path) -> list[SettlementRow]:
    with open(path, newline="", encoding="utf-8") as file:
        return [
            SettlementRow(
                tick=int(row["tick"]), population=int(row["population"]), births=int(row["births"]),
                deaths=int(row["deaths"]), old_age=int(row["old_age"]), stored_food=int(row["stored_food"]),
                shelters=int(row["shelters"]), stores=int(row["stores"]),
                fields_sown=int(row["fields_sown"]), fields_ripe=int(row["fields_ripe"]),
            )
            for row in csv.DictReader(file)
        ]


def winters(rows: list[SettlementRow], season_ticks: int) -> list[Winter]:
    """One entry per winter the run reached. Rows must close at the season boundaries."""
    by_tick = {row.tick: row for row in rows}
    year_ticks = 4 * season_ticks
    last_tick = rows[-1].tick if rows else 0
    result = []
    year = 0
    while year * year_ticks + 3 * season_ticks <= last_tick:
        start = by_tick.get(year * year_ticks + 3 * season_ticks)
        if start is None:
            break
        end = by_tick.get((year + 1) * year_ticks)
        after = end.population if end is not None else (0 if rows[-1].population == 0 else None)
        result.append(Winter(year + 1, start.population, after, start.stored_food, start.shelters, start.stores))
        year += 1
    return result


def died_out_at(rows: list[SettlementRow]) -> int | None:
    """The tick at which the population reached zero, if it did."""
    for row in rows:
        if row.population == 0:
            return row.tick
    return None


def survives(winter_list: list[Winter], count: int, founders: int, final_population: int) -> bool:
    """The step 3.6 and stage criterion: `count` winters, each with at least half surviving,
    and in the end no fewer NPCs than at the start."""
    return (len(winter_list) >= count and all(w.survived for w in winter_list[:count])
            and final_population >= founders)
