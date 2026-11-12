package com.pgoogol.npcworld.core;

/**
 * Stage 3: trees, carried items, fields and buildings. Growth times are ticks of full growth
 * (see {@link SeasonConfig}). {@code inventorySize == 0} means NPCs carry nothing (stages 1 and 2).
 */
public record EconomyConfig(
        double treeShare,
        int treeRegrowTicks,
        int inventorySize,
        int fieldGrowTicks,
        int fieldYield,
        int shelterWood,
        int shelterWork,
        int shelterCapacity,
        int storeWood,
        int storeWork,
        int storeCapacity) {

    /** Stages 1 and 2: none of it. */
    public static final EconomyConfig NONE = new EconomyConfig(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public EconomyConfig {
        if (treeShare < 0 || treeRegrowTicks < 0 || inventorySize < 0 || fieldGrowTicks < 0 || fieldYield < 0
                || shelterWood < 0 || shelterWork < 0 || shelterCapacity < 0
                || storeWood < 0 || storeWork < 0 || storeCapacity < 0) {
            throw new IllegalArgumentException("Economy settings must not be negative");
        }
    }
}
