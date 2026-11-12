"""The discovery map: building it, merging it and turning it into directions."""

from brain.discovery import CONSUME, STAY, DiscoveryMap, step_towards
from helpers import EMPTY, FOOD, LAVA, NEED_LEVELS, WALL, WATER, observation

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


UP, DOWN, LEFT, RIGHT = 1, 2, 3, 4


def water_target(seen, x=5, y=5, tick=0):
    return seen.target(observation(x=x, y=y, thirst=THIRSTY), NEED_LEVELS, tick)


def food_target(seen, x=5, y=5, tick=0):
    return seen.target(observation(x=x, y=y, hunger=HUNGRY), NEED_LEVELS, tick)


def test_target_is_the_nearest_known_water():
    seen = discovery()
    seen.water = {(9, 5), (5, 1), (1, 1)}

    # From (5, 5): (9, 5) and (5, 1) are 4 steps away, (1, 1) is 8. The tie goes to the smaller position.
    assert water_target(seen) == (5, 1)


def test_no_target_without_a_need_or_without_knowledge():
    seen = discovery()
    assert water_target(seen) is None
    assert food_target(seen) is None

    seen.water = {(1, 1)}
    seen.food[(2, 2)] = (0, True)
    assert seen.target(observation(thirst=10, hunger=10), NEED_LEVELS, tick=0) is None
    assert water_target(seen) == (1, 1)


def test_target_follows_the_more_pressing_need():
    seen = discovery()
    seen.water = {(5, 1)}
    seen.food[(9, 5)] = (0, True)

    def target(thirst, hunger):
        return seen.target(observation(x=5, y=5, thirst=thirst, hunger=hunger), NEED_LEVELS, tick=0)

    assert target(thirst=70, hunger=60) == (5, 1)
    assert target(thirst=60, hunger=70) == (9, 5)
    assert target(thirst=60, hunger=60) == (5, 1)     # a tie goes to water
    assert target(thirst=10, hunger=60) == (9, 5)     # a low need does not count
    assert target(thirst=60, hunger=10) == (5, 1)


def test_empty_food_place_is_skipped_until_the_retry_time():
    seen = discovery(retry=50)
    seen.food[(5, 3)] = (100, False)   # near, seen empty at tick 100
    seen.food[(9, 5)] = (40, True)     # farther, had food

    assert food_target(seen, tick=120) == (9, 5)
    assert food_target(seen, tick=150) == (5, 3)   # worth another try


def test_step_goes_straight_towards_the_target():
    here = observation(x=5, y=5)

    assert step_towards(here, (5, 1)) == UP
    assert step_towards(here, (5, 9)) == DOWN
    assert step_towards(here, (1, 5)) == LEFT
    assert step_towards(here, (9, 5)) == RIGHT


def test_step_on_the_target_is_to_consume():
    assert step_towards(observation(x=5, y=5), (5, 5)) == CONSUME


def test_step_never_enters_lava_or_a_wall():
    lava_above = observation(x=5, y=5, cells=cells(c1=LAVA))
    wall_above = observation(x=5, y=5, cells=cells(c1=WALL))

    # The way up is blocked; a sideways step keeps the distance smallest among the safe ones.
    assert step_towards(lava_above, (5, 1)) in (LEFT, RIGHT)
    assert step_towards(wall_above, (5, 1)) in (LEFT, RIGHT)


def test_step_takes_the_free_axis_on_a_diagonal():
    lava_above = observation(x=5, y=5, cells=cells(c1=LAVA))

    assert step_towards(lava_above, (8, 2)) == RIGHT


def test_no_safe_step_means_staying():
    boxed_in = observation(x=5, y=5, cells=cells(c1=LAVA, c3=WALL, c5=LAVA, c7=WALL))

    assert step_towards(boxed_in, (5, 1)) == STAY


def test_export_and_import_keep_the_map():
    seen = discovery()
    seen.see(observation(x=5, y=5, cells=cells(c0=WATER, c4=FOOD)), tick=7)

    restored = discovery()
    restored.import_state(seen.export_state())

    assert restored.water == seen.water
    assert restored.food == seen.food
