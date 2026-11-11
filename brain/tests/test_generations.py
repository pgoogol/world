"""Measuring how fast generations learn from the episode log."""

from brain.generations import NpcLife, age_at_level, median_age_at_level, read_lives, report


def life(npc_id, generation, *episodes) -> NpcLife:
    return NpcLife(npc_id, generation, list(episodes))


def test_reads_only_episode_rows(tmp_path):
    file = tmp_path / "episodes.csv"
    file.write_text(
        "tick,event,npc_id,generation,npc_age,value\n"
        "700,episode,3,0,650,120\n"
        "900,retired,3,0,850,0\n"
        "900,born,12,1,0,5\n"
        "1400,episode,12,1,500,480\n"
        "1500,episode,3,0,800,90\n",
        encoding="utf-8",
    )

    lives = read_lives(file)

    assert lives[3] == life(3, 0, (650, 120), (800, 90))
    assert lives[12] == life(12, 1, (500, 480))


def test_age_at_level_uses_a_rolling_mean_of_episode_lengths():
    npc = life(1, 1, (100, 100), (700, 600), (1000, 300), (1500, 500), (2100, 600))

    # Means of two: 350, 450, 400, 550. The level 440 is first reached with the episode that ended at age 1000.
    assert age_at_level(npc, level=440, rolling=2) == 1000
    assert age_at_level(npc, level=560, rolling=2) is None


def test_age_at_level_needs_enough_episodes():
    assert age_at_level(life(1, 1, (900, 900)), level=440, rolling=2) is None


def test_median_counts_npcs_that_never_reached_the_level_as_slowest():
    fast = life(1, 1, (500, 500), (1000, 500))
    slow = life(2, 1, (100, 100), (200, 100), (900, 700), (1600, 700))
    never = life(3, 1, (100, 100), (200, 100))

    assert median_age_at_level([fast, slow, never], level=440, rolling=2) == 1600
    assert median_age_at_level([fast, never, never], level=440, rolling=2) is None
    assert median_age_at_level([fast], level=440, rolling=2) == 1000
    assert median_age_at_level([], level=440, rolling=2) is None


def test_report_groups_npcs_by_generation():
    lives = {
        1: life(1, 0, (100, 100), (200, 100), (5000, 300)),
        2: life(2, 1, (500, 500), (1000, 500), (4000, 800)),
        3: life(3, 1, (450, 450), (950, 500), (6000, 600)),
    }

    founders, children = report(lives, level=440, rolling=2, adult_age=3000)

    assert (founders.generation, founders.npcs, founders.reached_level) == (0, 1, 0)
    assert founders.median_age_at_level is None
    assert founders.adult_mean_life == 300
    assert (children.generation, children.npcs, children.reached_level) == (1, 2, 2)
    assert children.median_age_at_level == 950
    assert children.adult_mean_life == 700
