package com.pgoogol.npcworld.core;

import java.util.List;

/** Test fixtures: stage 1 values with switches for the parts a test wants to change. */
final class TestConfigs {

    long seed = 42;
    int width = 20;
    int height = 20;
    int npcs = 5;
    double foodShare = 0.08;
    double waterShare = 0.05;
    double lavaShare = 0.05;
    int foodRegrowTicks = 50;
    int consumeRelief = 40;
    int energyRegen = 0;
    int lifeLimit = 2000;
    long lifespanTicks = 0;
    double lifespanSpread = 0;
    long agingStartTick = 0;
    int viewRadius = 1;
    SeasonConfig seasons = SeasonConfig.NONE;
    EconomyConfig economy = EconomyConfig.NONE;
    PopulationConfig population = PopulationConfig.CLASSIC;
    double winterSurvived = 0;

    static TestConfigs stage1() {
        return new TestConfigs();
    }

    /** No food, water or lava: only the wall. */
    static TestConfigs emptyBoard() {
        TestConfigs configs = new TestConfigs();
        configs.foodShare = 0;
        configs.waterShare = 0;
        configs.lavaShare = 0;
        return configs;
    }

    /** Stage 3 economy on an empty board: small, round numbers that tests can count by hand. */
    static EconomyConfig economy() {
        return new EconomyConfig(0, 10, 4, 10, 4, 3, 2, 2, 2, 1, 10);
    }

    TestConfigs seed(long value) {
        seed = value;
        return this;
    }

    TestConfigs size(int value) {
        width = value;
        height = value;
        return this;
    }

    TestConfigs npcs(int value) {
        npcs = value;
        return this;
    }

    TestConfigs foodRegrowTicks(int value) {
        foodRegrowTicks = value;
        return this;
    }

    TestConfigs energyRegen(int value) {
        energyRegen = value;
        return this;
    }

    TestConfigs lifeLimit(int value) {
        lifeLimit = value;
        return this;
    }

    TestConfigs lifespan(long ticks, double spread) {
        lifespanTicks = ticks;
        lifespanSpread = spread;
        return this;
    }

    TestConfigs agingStartTick(long value) {
        agingStartTick = value;
        return this;
    }

    TestConfigs viewRadius(int value) {
        viewRadius = value;
        return this;
    }

    /** Seasons of {@code ticks} each with full, full, half and no growth, cold of 1 and winter rot. */
    TestConfigs seasons(int ticks) {
        seasons = new SeasonConfig(ticks, List.of(100, 100, 50, 0), 1, true);
        return this;
    }

    TestConfigs seasons(SeasonConfig value) {
        seasons = value;
        return this;
    }

    TestConfigs economy(EconomyConfig value) {
        economy = value;
        return this;
    }

    TestConfigs population(PopulationConfig value) {
        population = value;
        return this;
    }

    TestConfigs winterSurvived(double value) {
        winterSurvived = value;
        return this;
    }

    WorldConfig build() {
        return new WorldConfig(seed, width, height, npcs, foodShare, waterShare, lavaShare, foodRegrowTicks,
                1, 100, 80, 1, 25, consumeRelief, 100, energyRegen, lifeLimit, lifespanTicks, lifespanSpread,
                agingStartTick, viewRadius, seasons, economy, population,
                new RewardConfig(0.1, 1.0, 50, -10.0, winterSurvived));
    }
}
