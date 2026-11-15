"""The place map of stage 3: sightings, contacts, winter and finding the way."""

from brain.places import PlaceMap, move_towards, nearest
from brain.proto import brain_pb2 as pb

E, F, W, T, S, H, L, X, R = (pb.CELL_EMPTY, pb.CELL_FOOD, pb.CELL_WATER, pb.CELL_TREE, pb.CELL_STORE,
                             pb.CELL_SHELTER, pb.CELL_LAVA, pb.CELL_WALL, pb.CELL_FIELD_RIPE)
SPRING, WINTER = 1, 4


def view(x, y, places=None, size=3, here=0, season=SPRING):
    """A view of `size` x `size` empty cells; `places` maps (dx, dy) from the NPC to a cell type."""
    radius = size // 2
    cells = [E] * (size * size)
    for (dx, dy), cell in (places or {}).items():
        cells[(dy + radius) * size + dx + radius] = cell
    return pb.Observation(surroundings=cells, view_size=size, x=x, y=y, here_amount=here, season=season, energy=100)


def test_map_places_every_seen_cell_on_the_board():
    seen = PlaceMap(50)

    seen.see(view(10, 10, {(-3, -3): W, (0, 0): T, (3, 2): H}, size=7), tick=4)

    assert seen.places(pb.CELL_WATER) == [(7, 7)]
    assert seen.places(pb.CELL_TREE) == [(10, 10)]
    assert seen.places(pb.CELL_SHELTER) == [(13, 12)]
    assert seen.cell((9, 9)) == E


def test_map_remembers_the_amount_under_the_npc():
    seen = PlaceMap(50)

    seen.see(view(5, 5, {(0, 0): S}, here=17), tick=1)
    seen.see(view(6, 5, {(-1, 0): S}), tick=2)       # seen from the side: the amount is not visible

    assert seen.amount((5, 5)) == 17


def test_eaten_food_place_is_worth_a_visit_after_the_retry_time():
    seen = PlaceMap(50)
    seen.see(view(5, 5, {(0, 0): F}), tick=1)
    seen.see(view(5, 5), tick=10)

    assert seen.food_places(tick=40, season=SPRING) == []
    assert seen.food_places(tick=60, season=SPRING) == [(5, 5)]
    assert seen.food_places(tick=60, season=WINTER) == []


def test_food_place_found_empty_again_waits_another_retry_time():
    seen = PlaceMap(50)
    seen.see(view(5, 5, {(0, 0): F}), tick=1)
    seen.see(view(5, 5), tick=10)

    seen.see(view(5, 5), tick=70)

    assert seen.food_places(tick=80, season=SPRING) == []


def test_winter_rot_forgets_wild_food_and_ripe_fields():
    seen = PlaceMap(50)
    seen.see(view(5, 5, {(0, 0): F, (1, 0): R, (-1, 0): S}), tick=1)

    seen.winter_rot(tick=1500)

    assert seen.places(pb.CELL_FOOD, pb.CELL_FIELD_RIPE) == []
    assert seen.places(pb.CELL_STORE) == [(4, 5)]


def test_contact_passes_on_what_the_other_has_seen():
    mine, theirs = PlaceMap(50), PlaceMap(50)
    mine.see(view(2, 2, {(0, 0): W}), tick=1)
    theirs.see(view(20, 20, {(0, 0): T}), tick=1)

    mine.merge(theirs, other_id=1)
    theirs.merge(mine, other_id=0)

    assert mine.places(pb.CELL_TREE) == [(20, 20)]
    assert theirs.places(pb.CELL_WATER) == [(2, 2)]


def test_contact_keeps_the_more_recent_sighting():
    mine, theirs = PlaceMap(50), PlaceMap(50)
    mine.see(view(5, 5, {(0, 0): F}), tick=1)
    theirs.see(view(5, 5), tick=30)                    # later: the food is gone
    mine.merge(theirs, other_id=1)
    assert mine.cell((5, 5)) == E

    older, newer = PlaceMap(50), PlaceMap(50)
    older.see(view(5, 5), tick=1)
    newer.see(view(5, 5, {(0, 0): H}), tick=30)
    newer.merge(older, other_id=0)
    assert newer.cell((5, 5)) == H


def test_knowledge_passes_through_a_third_npc():
    first, second, third = PlaceMap(50), PlaceMap(50), PlaceMap(50)
    first.see(view(2, 2, {(0, 0): W}), tick=1)

    second.merge(first, other_id=0)
    third.merge(second, other_id=1)

    assert third.places(pb.CELL_WATER) == [(2, 2)]


def test_second_contact_takes_only_what_is_new():
    mine, theirs = PlaceMap(50), PlaceMap(50)
    theirs.see(view(5, 5, {(0, 0): W}), tick=1)
    mine.merge(theirs, other_id=1)
    known = len(mine.log)

    mine.merge(theirs, other_id=1)

    assert len(mine.log) == known


def test_nearest_counts_steps():
    assert nearest(5, 5, [(9, 5), (5, 7), (0, 0)]) == (5, 7)
    assert nearest(5, 5, []) is None


def test_move_towards_takes_the_longer_axis_first():
    assert move_towards(view(5, 5, size=7), (9, 6)) == pb.ACTION_RIGHT
    assert move_towards(view(5, 5, size=7), (5, 2)) == pb.ACTION_UP
    assert move_towards(view(5, 5, size=7), (5, 5)) == pb.ACTION_STAY


def test_move_towards_never_steps_on_visible_lava_or_into_a_wall():
    assert move_towards(view(5, 5, {(1, 0): L}, size=7), (9, 6)) == pb.ACTION_DOWN
    assert move_towards(view(5, 5, {(0, -1): X}, size=7), (6, 2)) == pb.ACTION_RIGHT


def test_move_towards_walks_around_an_obstacle_in_the_way():
    # Lava straight ahead, the target right behind it: step aside instead of standing still.
    action = move_towards(view(5, 5, {(1, 0): L}, size=7), (8, 5))

    assert action in (pb.ACTION_UP, pb.ACTION_DOWN)
