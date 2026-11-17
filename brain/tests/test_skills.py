"""Step 3.10: every composite skill turns into the right single move, and is offered only when it can work."""

import random

from brain.places import PlaceMap
from brain.proto import brain_pb2 as pb
from brain.skills import (
    BUILD_SHELTER,
    BUILD_STORE,
    DRINK,
    EAT,
    EXPLORE,
    GATHER_FOOD,
    GATHER_WOOD,
    PLANT,
    STORE_FOOD,
    TAKE_FOOD,
    TAKE_SHELTER,
    Member,
    RawActions,
    Situation,
    Skills,
    note_full_shelter,
)

E, F, W, T, S, H, L, X, R = (pb.CELL_EMPTY, pb.CELL_FOOD, pb.CELL_WATER, pb.CELL_TREE, pb.CELL_STORE,
                             pb.CELL_SHELTER, pb.CELL_LAVA, pb.CELL_WALL, pb.CELL_FIELD_RIPE)
SITE_H, SITE_S = pb.CELL_SITE_SHELTER, pb.CELL_SITE_STORE
SPRING, WINTER = 1, 4
SKILLS = Skills(inventory_size=4)
# The control settlement of the stage: the skills that are left without planting, storing and building.
CONTROL = Skills(4, enabled=frozenset((DRINK, EAT, GATHER_FOOD, GATHER_WOOD, EXPLORE)))


def new_member(seed=1):
    return Member(0, random.Random(seed), PlaceMap(50), tick=0)


def situation(places=None, *, x=10, y=10, food=0, wood=0, here=0, season=SPRING, hunger=0, thirst=0, energy=100,
              member=None, tick=5):
    """An NPC with a 7x7 view; `places` maps (dx, dy) from the NPC to a cell type."""
    cells = [E] * 49
    for (dx, dy), cell in (places or {}).items():
        cells[(dy + 3) * 7 + dx + 3] = cell
    obs = pb.Observation(surroundings=cells, view_size=7, x=x, y=y, carried_food=food, carried_wood=wood,
                         here_amount=here, season=season, hunger=hunger, thirst=thirst, energy=energy)
    member = member or new_member()
    member.map.see(obs, tick)
    return Situation(member, obs, tick)


def offered(s, skills=SKILLS):
    return {skills.names[i] for i, allowed in enumerate(skills.mask(s)) if allowed}


def test_with_nothing_known_only_exploring_and_starting_a_site_are_offered():
    assert offered(situation()) == {"explore", "build_shelter", "build_store"}


def test_drink_walks_to_known_water_and_drinks_on_it():
    assert SKILLS.action(DRINK, situation({(2, 0): W})) == pb.ACTION_RIGHT
    assert SKILLS.action(DRINK, situation({(0, 0): W})) == pb.ACTION_CONSUME
    assert "drink" in offered(situation({(2, 0): W}))


def test_eat_takes_food_from_the_ground_before_the_carried_portion():
    assert SKILLS.action(EAT, situation({(0, 0): F}, food=2)) == pb.ACTION_CONSUME
    assert SKILLS.action(EAT, situation({(0, 0): R}, here=3)) == pb.ACTION_CONSUME
    assert SKILLS.action(EAT, situation({(0, 0): S}, here=5)) == pb.ACTION_CONSUME
    assert SKILLS.action(EAT, situation(food=2)) == pb.ACTION_EAT_CARRIED


def test_eat_walks_to_the_nearer_of_wild_food_and_a_stocked_store():
    member = new_member()
    situation({(0, 0): S}, x=12, y=10, here=8, member=member)        # the NPC has stood on a stocked store

    assert SKILLS.action(EAT, situation({(-3, 0): F, (2, 0): S}, member=member)) == pb.ACTION_RIGHT
    assert SKILLS.action(EAT, situation({(-1, 0): F, (2, 0): S}, member=member)) == pb.ACTION_LEFT


def test_eat_is_not_offered_when_the_only_store_is_empty():
    member = new_member()
    situation({(0, 0): S}, x=14, y=10, here=0, member=member)          # stood on it: empty

    assert "eat" not in offered(situation(member=member))


def test_store_seen_only_from_the_side_is_worth_a_look():
    hungry_newborn = situation({(2, 0): S}, hunger=70)

    assert "eat" in offered(hungry_newborn)
    assert SKILLS.action(EAT, hungry_newborn) == pb.ACTION_RIGHT


def test_gather_food_picks_up_and_needs_room():
    assert SKILLS.action(GATHER_FOOD, situation({(0, 0): F})) == pb.ACTION_GATHER
    assert SKILLS.action(GATHER_FOOD, situation({(0, 2): R})) == pb.ACTION_DOWN
    assert "gather_food" not in offered(situation({(0, 0): F}, food=2, wood=2))


def test_gather_wood_fells_the_tree_under_the_npc():
    assert SKILLS.action(GATHER_WOOD, situation({(0, 0): T})) == pb.ACTION_GATHER
    assert SKILLS.action(GATHER_WOOD, situation({(-2, 0): T})) == pb.ACTION_LEFT
    assert "gather_wood" not in offered(situation({(0, 0): T}, wood=4))


def test_store_food_carries_food_to_a_store():
    assert SKILLS.action(STORE_FOOD, situation({(0, -2): S}, food=3)) == pb.ACTION_UP
    assert SKILLS.action(STORE_FOOD, situation({(0, 0): S}, food=3)) == pb.ACTION_DEPOSIT
    assert "store_food" not in offered(situation({(0, 0): S}))


def test_take_food_withdraws_from_a_stocked_store():
    assert SKILLS.action(TAKE_FOOD, situation({(0, 0): S}, here=4)) == pb.ACTION_WITHDRAW
    assert "take_food" not in offered(situation({(0, 0): S}, here=0))
    assert "take_food" not in offered(situation({(0, 0): S}, here=4, wood=4))


def test_plant_needs_a_portion_and_an_empty_cell():
    assert SKILLS.action(PLANT, situation(food=1)) == pb.ACTION_PLANT
    assert "plant" in offered(situation(food=1))
    assert "plant" not in offered(situation())
    assert "plant" not in offered(situation({(0, 0): T}, food=1))


def test_plant_is_not_offered_where_wild_food_grows():
    member = new_member()
    situation({(0, 0): F}, member=member, tick=1)

    assert "plant" not in offered(situation(food=1, member=member, tick=2))


def test_build_starts_a_site_only_when_none_is_known():
    assert SKILLS.action(BUILD_SHELTER, situation()) == pb.ACTION_BUILD_SHELTER
    assert SKILLS.action(BUILD_SHELTER, situation({(3, 0): SITE_H}, wood=4)) == pb.ACTION_RIGHT
    assert SKILLS.action(BUILD_SHELTER, situation({(0, 0): SITE_H}, wood=1)) == pb.ACTION_BUILD_SHELTER


def test_shelter_and_store_sites_are_kept_apart():
    assert SKILLS.action(BUILD_STORE, situation({(3, 0): SITE_H})) == pb.ACTION_BUILD_STORE
    assert SKILLS.action(BUILD_STORE, situation({(3, 0): SITE_H, (0, 2): SITE_S}, wood=4)) == pb.ACTION_DOWN
    assert "build_store" not in offered(situation({(0, 0): T}))


def test_build_fetches_wood_for_a_site_that_lacks_it():
    places = {(3, 0): SITE_H, (-2, 0): T}

    assert SKILLS.action(BUILD_SHELTER, situation(places)) == pb.ACTION_LEFT
    assert SKILLS.action(BUILD_SHELTER, situation({(3, 0): SITE_H, (0, 0): T})) == pb.ACTION_GATHER
    assert SKILLS.action(BUILD_SHELTER, situation({(0, 0): SITE_H, (-2, 0): T}, here=2)) == pb.ACTION_LEFT


def test_build_brings_a_load_once_it_covers_what_the_site_lacks():
    member = new_member()
    situation({(0, 0): SITE_H}, x=15, y=10, here=4, member=member)       # seen with 4 of 6 wood

    assert SKILLS.action(BUILD_SHELTER, situation({(-2, 0): T}, wood=1, member=member)) == pb.ACTION_LEFT
    assert SKILLS.action(BUILD_SHELTER, situation({(-2, 0): T}, wood=2, member=member)) == pb.ACTION_RIGHT


def test_build_works_on_a_site_that_has_all_its_wood():
    assert SKILLS.action(BUILD_SHELTER, situation({(0, 0): SITE_H}, here=6)) == pb.ACTION_BUILD_SHELTER
    assert SKILLS.action(BUILD_STORE, situation({(0, 0): SITE_S}, here=4)) == pb.ACTION_BUILD_STORE


def test_new_site_goes_next_to_the_store():
    far = situation({(3, 0): S}, x=10, y=10)
    assert SKILLS.action(BUILD_SHELTER, situation({(3, 3): S})) == pb.ACTION_RIGHT
    assert SKILLS.action(BUILD_SHELTER, far) == pb.ACTION_BUILD_SHELTER
    assert SKILLS.action(BUILD_SHELTER, situation({(0, 0): T, (1, 0): S})) in (pb.ACTION_UP, pb.ACTION_DOWN)


def test_without_a_store_the_first_site_goes_next_to_water():
    assert SKILLS.action(BUILD_STORE, situation({(-3, -3): W})) == pb.ACTION_LEFT
    assert SKILLS.action(BUILD_STORE, situation({(-1, -1): W})) == pb.ACTION_BUILD_STORE


def test_take_shelter_walks_to_a_shelter_and_stays():
    assert SKILLS.action(TAKE_SHELTER, situation({(0, 3): H})) == pb.ACTION_DOWN
    assert SKILLS.action(TAKE_SHELTER, situation({(0, 0): H})) == pb.ACTION_STAY
    assert "take_shelter" not in offered(situation())


def test_full_shelter_is_remembered_until_spring():
    member = new_member()
    places = {(0, 0): H, (3, 0): H}
    note_full_shelter(member, situation(places, season=WINTER, energy=90, member=member), need_high=80)

    cold = situation(places, season=WINTER, energy=89, member=member)
    note_full_shelter(member, cold, need_high=80)

    assert SKILLS.action(TAKE_SHELTER, cold) == pb.ACTION_RIGHT
    note_full_shelter(member, situation(places, season=SPRING, member=member), need_high=80)
    assert member.full_shelters == set()


def test_hunger_in_a_shelter_does_not_mark_it_full():
    member = new_member()
    note_full_shelter(member, situation({(0, 0): H}, season=WINTER, energy=90, member=member), need_high=80)

    note_full_shelter(member, situation({(0, 0): H}, season=WINTER, energy=89, hunger=85, member=member), 80)

    assert member.full_shelters == set()


def test_explore_keeps_one_direction_for_a_while():
    member = new_member(seed=3)
    first = SKILLS.action(EXPLORE, situation(member=member, tick=1))
    target = member.explore_target

    SKILLS.action(EXPLORE, situation(member=member, tick=2))

    assert first in (pb.ACTION_UP, pb.ACTION_DOWN, pb.ACTION_LEFT, pb.ACTION_RIGHT)
    assert member.explore_target == target


def test_control_settlement_is_never_offered_planting_storing_or_building():
    s = situation({(0, 0): S, (1, 0): H, (2, 0): W, (0, 1): F, (0, 2): T}, food=2, here=5)

    assert offered(s, CONTROL) == {"drink", "eat", "gather_food", "gather_wood", "explore"}


def test_raw_actions_offer_the_walk_only_towards_a_needed_known_place():
    raw = RawActions(need_low=50)

    assert raw.mask(situation({(2, 0): W}))[6] is False
    thirsty = situation({(2, 0): W}, thirst=60)
    assert raw.mask(thirsty)[6] is True
    assert raw.action(6, thirsty) == pb.ACTION_RIGHT
    assert raw.action(6, situation({(0, 0): W}, thirst=60)) == pb.ACTION_CONSUME
    assert raw.action(5, thirsty) == pb.ACTION_CONSUME


def test_new_store_only_when_every_known_store_is_full():
    member = new_member()
    situation({(0, 0): S}, x=14, y=10, here=40, member=member)          # a store with room left

    assert "build_store" not in offered(situation(member=member))
    assert "build_store" in offered(situation({(2, 0): SITE_S}, member=member))   # joining a site is fine

    full = new_member()
    situation({(0, 0): S}, x=14, y=10, here=80, member=full)
    assert "build_store" in offered(situation(member=full))


def test_store_seen_only_from_the_side_counts_as_not_full():
    assert "build_store" not in offered(situation({(2, 0): S}))
    assert "build_store" in offered(situation())


def test_only_a_building_skill_without_an_open_site_starts_one():
    assert Skills.starts_site(BUILD_SHELTER, situation())
    assert not Skills.starts_site(BUILD_SHELTER, situation({(3, 0): SITE_H}))
    assert not Skills.starts_site(BUILD_SHELTER, situation({(0, 0): SITE_H}))
    assert Skills.starts_site(BUILD_STORE, situation({(3, 0): SITE_H}))
    assert not Skills.starts_site(DRINK, situation())
