package com.pgoogol.npcworld.core;

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
    int energyRegen = 0;
    int lifeLimit = 2000;

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

    WorldConfig build() {
        return new WorldConfig(seed, width, height, npcs, foodShare, waterShare, lavaShare, foodRegrowTicks,
                1, 100, 80, 1, 25, 40, 100, energyRegen, lifeLimit, new RewardConfig(0.1, 1.0, 50, -10.0));
    }
}
