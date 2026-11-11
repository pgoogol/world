package com.pgoogol.npcworld.core;

/** World parameters. Values come from the stage configuration file, never from code. */
public record WorldConfig(
        long seed,
        int width,
        int height,
        int npcs,
        double foodShare,
        double waterShare,
        double lavaShare,
        int foodRegrowTicks,
        int needPerTick,
        int needMax,
        int needThreshold,
        int needEnergyLoss,
        int lavaEnergyLoss,
        int consumeRelief,
        int energyMax,
        int energyRegen,
        int lifeLimit,
        long lifespanTicks,
        double lifespanSpread,
        long agingStartTick,
        RewardConfig rewards) {

    public WorldConfig {
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("Board must be at least 3x3 to fit the surrounding wall");
        }
        if (npcs < 1) {
            throw new IllegalArgumentException("At least one NPC is required");
        }
        if (foodShare < 0 || waterShare < 0 || lavaShare < 0 || foodShare + waterShare + lavaShare >= 1) {
            throw new IllegalArgumentException("Cell shares must be non-negative and leave empty cells");
        }
        if (lifespanTicks < 0 || lifespanSpread < 0 || lifespanSpread >= 1 || agingStartTick < 0) {
            throw new IllegalArgumentException("Lifespan and ageing start must not be negative, spread must be in [0, 1)");
        }
    }

    /** Stage 2: with a positive lifespan NPCs die of old age and newborns replace them. */
    public boolean agingEnabled() {
        return lifespanTicks > 0;
    }
}
