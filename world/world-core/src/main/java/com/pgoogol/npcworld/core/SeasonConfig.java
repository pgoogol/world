package com.pgoogol.npcworld.core;

import java.util.List;

/**
 * Stage 3: the year. Four seasons of {@code seasonTicks} each: spring, summer, autumn, winter.
 *
 * <p>Everything that grows back (wild food, trees, fields) counts growth points: 100 per tick of
 * full growth. {@code growthPercent} gives the points per tick in each season.
 * {@code seasonTicks == 0} switches seasons off: growth is always full and there is no winter.
 */
public record SeasonConfig(int seasonTicks, List<Integer> growthPercent, int coldEnergyLoss, boolean winterRot) {

    /** Stages 1 and 2: no seasons. */
    public static final SeasonConfig NONE = new SeasonConfig(0, List.of(100, 100, 100, 100), 0, false);

    public SeasonConfig {
        growthPercent = List.copyOf(growthPercent);
        if (seasonTicks < 0 || coldEnergyLoss < 0) {
            throw new IllegalArgumentException("Season length and cold must not be negative");
        }
        if (growthPercent.size() != Season.values().length || growthPercent.stream().anyMatch(p -> p < 0)) {
            throw new IllegalArgumentException("Growth needs one non-negative percent per season");
        }
    }

    public boolean enabled() {
        return seasonTicks > 0;
    }

    /** The season of a tick; without seasons there is none. */
    public Season seasonAt(long tick) {
        if (!enabled()) {
            return null;
        }
        return Season.values()[(int) ((tick / seasonTicks) % Season.values().length)];
    }

    /** Growth points per tick; 100 is full growth. */
    public int growthAt(long tick) {
        Season season = seasonAt(tick);
        return season == null ? 100 : growthPercent.get(season.ordinal());
    }

    /** Ticks until the season changes, counting the current one; 0 without seasons. */
    public int ticksLeftAt(long tick) {
        return enabled() ? (int) (seasonTicks - tick % seasonTicks) : 0;
    }
}
