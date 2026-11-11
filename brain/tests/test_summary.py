"""Reading training statistics and comparing variants."""

import pytest

from brain.summary import Window, first_tick_reaching, mean_life, read_windows, trained_mean_life


def test_reads_the_csv_written_by_the_world(tmp_path):
    file = tmp_path / "stats.csv"
    file.write_text("tick,lives_ended,mean_life_length\n1000,4,125.50\n2000,0,\n", encoding="utf-8")

    assert read_windows(file) == [Window(1000, 4, 125.5), Window(2000, 0, 0.0)]


def test_mean_life_weights_windows_by_the_number_of_lives():
    windows = [Window(1000, 3, 100.0), Window(2000, 1, 500.0), Window(3000, 0, 0.0)]

    assert mean_life(windows) == pytest.approx(200.0)


def test_mean_life_needs_at_least_one_life():
    with pytest.raises(ValueError):
        mean_life([Window(1000, 0, 0.0)])


def test_trained_mean_looks_only_at_the_last_windows():
    windows = [Window(1000, 10, 50.0), Window(2000, 2, 800.0), Window(3000, 2, 1000.0)]

    assert trained_mean_life(windows, last_windows=2) == pytest.approx(900.0)


def test_threshold_tick_uses_a_rolling_mean():
    windows = [Window(1000, 5, 100.0), Window(2000, 1, 2000.0), Window(3000, 2, 600.0), Window(4000, 2, 700.0)]

    # One lucky life in window 2 is not enough: (5*100 + 2000) / 6 < 600.
    assert first_tick_reaching(windows, threshold=600.0, rolling_windows=2) == 3000


def test_threshold_tick_is_none_when_never_reached():
    assert first_tick_reaching([Window(1000, 5, 100.0)] * 5, threshold=600.0, rolling_windows=2) is None
