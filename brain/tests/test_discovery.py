"""The discovery map: building it, merging it and turning it into directions."""

from brain.discovery import NO_DIRECTION, DiscoveryMap
from helpers import EMPTY, FOOD, LAVA, NEED_LEVELS, WATER, observation

THIRSTY = 60
HUNGRY = 60


def discovery(retry=50) -> DiscoveryMap:
    return DiscoveryMap(food_retry_ticks=retry)


def cells(**places) -> list[int]:
    """Nine empty cells with some replaced; keys are row-by-row indices like c0..c8."""
    result = [EMPTY] * 9
    for key, value in places.items():
        result[int(key[1:])] = value
    return result


def test_map_records_water_and_food_at_board_positions():
    seen = discovery()

    seen.see(observation(x=5, y=5, cells=cells(c0=WATER, c4=FOOD, c8=LAVA)), tick=7)

    assert seen.water == {(4, 4)}
    assert seen.food == {(5, 5): (7, True)}


def test_food_place_seen_empty_stays_on_the_map_as_empty():
    seen = discovery()
    seen.see(observation(x=5, y=5, cells=cells(c4=FOOD)), tick=1)

    seen.see(observation(x=5, y=5), tick=9)

    assert seen.food == {(5, 5): (9, False)}


def test_empty_cells_that_never_held_food_are_not_recorded():
    seen = discovery()

    seen.see(observation(x=5, y=5), tick=1)

    assert seen.water == set()
    assert seen.food == {}


def test_merge_takes_the_union_and_the_newer_food_sighting():
    mine, theirs = discovery(), discovery()
    mine.see(observation(x=2, y=2, cells=cells(c4=WATER, c5=FOOD)), tick=10)
    theirs.see(observation(x=3, y=2), tick=20)                           # saw the food place (3, 2) empty later
    theirs.see(observation(x=8, y=8, cells=cells(c4=WATER, c0=FOOD)), tick=5)

    mine.merge(theirs)

    assert mine.water == {(2, 2), (8, 8)}
    assert mine.food == {(7, 7): (5, True)} | {(3, 2): (10, True)}


def test_merge_prefers_the_more_recent_sighting():
    older, newer = discovery(), discovery()
    older.food[(3, 3)] = (10, True)
    newer.food[(3, 3)] = (30, False)

    older.merge(newer)
    assert older.food[(3, 3)] == (30, False)

    newer.food[(3, 3)] = (5, True)
    older.merge(newer)
    assert older.food[(3, 3)] == (30, False)


def test_direction_points_at_the_nearest_known_water():
    seen = discovery()
    seen.water = {(9, 5), (5, 1), (1, 1)}

    # From (5, 5): water at (9, 5) and (5, 1) is 4 steps away, (1, 1) is 8. The tie goes to the smaller position.
    directions = seen.directions(observation(x=5, y=5, thirst=THIRSTY), NEED_LEVELS, tick=0)

    assert directions[0] == (0 + 1) * 3 + (-1 + 1)   # dx = 0, dy < 0: straight up


def test_direction_codes_cover_all_eight_sides_and_here():
    seen = discovery()
    codes = {}
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            seen.water = {(5 + 2 * dx, 5 + 2 * dy)}
            codes[(dx, dy)] = seen.directions(observation(x=5, y=5, thirst=THIRSTY), NEED_LEVELS, tick=0)[0]

    assert sorted(codes.values()) == list(range(9))
    assert codes[(0, 0)] == 4


def test_no_direction_without_a_need_or_without_knowledge():
    seen = discovery()
    assert seen.directions(observation(thirst=THIRSTY, hunger=HUNGRY), NEED_LEVELS, tick=0) == bytes(
        (NO_DIRECTION, NO_DIRECTION)
    )

    seen.water = {(1, 1)}
    seen.food[(2, 2)] = (0, True)
    assert seen.directions(observation(thirst=10, hunger=10), NEED_LEVELS, tick=0) == bytes(
        (NO_DIRECTION, NO_DIRECTION)
    )
    assert seen.directions(observation(thirst=THIRSTY, hunger=10), NEED_LEVELS, tick=0)[1] == NO_DIRECTION
    assert seen.directions(observation(thirst=THIRSTY, hunger=10), NEED_LEVELS, tick=0)[0] != NO_DIRECTION


def test_empty_food_place_is_skipped_until_the_retry_time():
    seen = discovery(retry=50)
    seen.food[(5, 3)] = (100, False)   # near, seen empty at tick 100
    seen.food[(9, 5)] = (40, True)     # farther, had food

    early = seen.directions(observation(x=5, y=5, hunger=HUNGRY), NEED_LEVELS, tick=120)[1]
    later = seen.directions(observation(x=5, y=5, hunger=HUNGRY), NEED_LEVELS, tick=150)[1]

    assert early == (1 + 1) * 3 + (0 + 1)    # to the right: the far place
    assert later == (0 + 1) * 3 + (-1 + 1)   # up: the near place is worth another try


def test_export_and_import_keep_the_map():
    seen = discovery()
    seen.see(observation(x=5, y=5, cells=cells(c0=WATER, c4=FOOD)), tick=7)

    restored = discovery()
    restored.import_state(seen.export_state())

    assert restored.water == seen.water
    assert restored.food == seen.food
