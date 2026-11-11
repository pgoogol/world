"""Observation encoding: 9 cells plus hunger and thirst in three levels."""

import pytest

from brain.encoding import encode, need_level
from brain.proto import brain_pb2
from helpers import EMPTY, FOOD, LAVA, NEED_LEVELS, WALL, WATER, observation


@pytest.mark.parametrize(
    ("value", "level"),
    [(0, 0), (49, 0), (50, 1), (79, 1), (80, 2), (100, 2)],
)
def test_need_levels_switch_at_the_configured_bounds(value, level):
    assert need_level(value, NEED_LEVELS) == level


def test_key_holds_cells_row_by_row_then_hunger_and_thirst_levels():
    cells = [WALL, WALL, WALL, EMPTY, FOOD, WATER, LAVA, EMPTY, EMPTY]
    obs = brain_pb2.Observation(surroundings=cells, hunger=60, thirst=90, energy=10)

    assert encode(obs, NEED_LEVELS) == bytes([*cells, 1, 2])


def test_values_within_one_level_share_a_key():
    assert encode(observation(hunger=50), NEED_LEVELS) == encode(observation(hunger=79), NEED_LEVELS)
    assert encode(observation(hunger=49), NEED_LEVELS) != encode(observation(hunger=50), NEED_LEVELS)
    assert encode(observation(thirst=79), NEED_LEVELS) != encode(observation(thirst=80), NEED_LEVELS)


def test_energy_is_not_part_of_the_key():
    assert encode(observation(energy=100), NEED_LEVELS) == encode(observation(energy=1), NEED_LEVELS)


def test_different_surroundings_give_different_keys():
    assert encode(observation(centre=FOOD), NEED_LEVELS) != encode(observation(centre=LAVA), NEED_LEVELS)


def test_wrong_number_of_cells_is_an_error():
    obs = brain_pb2.Observation(surroundings=[EMPTY] * 8)

    with pytest.raises(ValueError, match="9 surrounding cells"):
        encode(obs, NEED_LEVELS)


def test_unspecified_cell_is_an_error():
    obs = brain_pb2.Observation(surroundings=[EMPTY] * 8 + [brain_pb2.CELL_UNSPECIFIED])

    with pytest.raises(ValueError, match="unspecified"):
        encode(obs, NEED_LEVELS)
