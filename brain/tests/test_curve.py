"""The learning curve of step 3.11: episodes averaged in blocks."""

from brain.curve import COLUMNS, blocks, read_episodes

HEADER = ("episode,end_tick,ticks,winters,population,births,deaths,old_age,fields_sown,fields_harvested,"
          "shelters_built,stores_built,stored_food_winter,population_winter")


def write(path, rows):
    path.write_text("\n".join([HEADER, *rows]) + "\n", encoding="utf-8")
    return path


def test_blocks_average_consecutive_episodes(tmp_path):
    file = write(tmp_path / "curve.csv", [
        "0,200,200,0,0,0,20,0,0,0,0,0,0,0",
        "1,600,400,0,0,0,20,0,2,0,0,0,0,0",
        "2,2600,2000,1,0,0,20,0,4,2,2,1,30,18",
        "3,6600,4000,2,5,1,15,0,8,6,4,1,50,16",
    ])

    first, second = blocks(read_episodes(file), 2)

    assert (first.first_episode, first.episodes, first.end_tick) == (0, 2, 600)
    assert first.means["ticks"] == 300 and first.means["fields_sown"] == 1
    assert (second.first_episode, second.end_tick) == (2, 6600)
    assert second.means == {"ticks": 3000, "winters": 1.5, "fields_sown": 6, "fields_harvested": 4,
                            "shelters_built": 3, "stores_built": 1, "stored_food_winter": 40, "births": 0.5}
    assert set(second.means) == set(COLUMNS)


def test_last_block_takes_the_remaining_episodes(tmp_path):
    file = write(tmp_path / "curve.csv", [f"{i},{100 * (i + 1)},100,0,0,0,0,0,0,0,0,0,0,0" for i in range(5)])

    result = blocks(read_episodes(file), 2)

    assert [block.episodes for block in result] == [2, 3]


def test_fewer_episodes_than_blocks_gives_one_block_per_episode(tmp_path):
    file = write(tmp_path / "curve.csv", ["0,100,100,0,0,0,0,0,0,0,0,0,0,0"])

    assert len(blocks(read_episodes(file), 10)) == 1
    assert blocks([], 10) == []
