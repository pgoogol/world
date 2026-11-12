package com.pgoogol.npcworld.core;

/**
 * Who lives and dies, and how new NPCs appear.
 *
 * @param deathIsFinal        stage 3: a collapse kills for good; stages 1 and 2 respawn the NPC with its memory
 * @param newbornOnOldAge     stage 2: a newborn replaces every NPC that dies of old age
 * @param foundersRadius      founders start within this many cells of one random place; 0 = anywhere
 * @param maxNpcs             no births above this population; 0 = no births at all
 * @param birthFoodCost       portions a birth takes from a store; 0 = no births
 * @param birthReservePerNpc  stores must hold this much per living NPC on top of the cost
 * @param birthIntervalTicks  at least this many ticks between two births
 */
public record PopulationConfig(
        boolean deathIsFinal,
        boolean newbornOnOldAge,
        int foundersRadius,
        int maxNpcs,
        int birthFoodCost,
        int birthReservePerNpc,
        int birthIntervalTicks) {

    /** Stages 1 and 2. */
    public static final PopulationConfig CLASSIC = new PopulationConfig(false, true, 0, 0, 0, 0, 0);

    public PopulationConfig {
        if (foundersRadius < 0 || maxNpcs < 0 || birthFoodCost < 0 || birthReservePerNpc < 0 || birthIntervalTicks < 0) {
            throw new IllegalArgumentException("Population settings must not be negative");
        }
    }

    public boolean birthsEnabled() {
        return birthFoodCost > 0 && maxNpcs > 0;
    }
}
