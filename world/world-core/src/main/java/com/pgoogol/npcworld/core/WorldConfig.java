package com.pgoogol.npcworld.core;

/**
 * World parameters. Values come from the stage configuration file, never from code.
 * Settings added after stage 1 default to values that reproduce the earlier stages.
 */
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
        int viewRadius,
        SeasonConfig seasons,
        EconomyConfig economy,
        PopulationConfig population,
        RewardConfig rewards) {

    public WorldConfig {
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("Board must be at least 3x3 to fit the surrounding wall");
        }
        if (npcs < 1) {
            throw new IllegalArgumentException("At least one NPC is required");
        }
        double shares = foodShare + waterShare + lavaShare + economy.treeShare();
        if (foodShare < 0 || waterShare < 0 || lavaShare < 0 || shares >= 1) {
            throw new IllegalArgumentException("Cell shares must be non-negative and leave empty cells");
        }
        if (lifespanTicks < 0 || lifespanSpread < 0 || lifespanSpread >= 1 || agingStartTick < 0) {
            throw new IllegalArgumentException("Lifespan and ageing start must not be negative, spread must be in [0, 1)");
        }
        if (lifeLimit < 0) {
            throw new IllegalArgumentException("Life limit must not be negative (0 switches it off)");
        }
        if (viewRadius < 1) {
            throw new IllegalArgumentException("View radius must be at least 1");
        }
    }

    /** Stage 2: with a positive lifespan NPCs die of old age. */
    public boolean agingEnabled() {
        return lifespanTicks > 0;
    }

    /** Stage 1: an episode is cut after this many ticks without a collapse. */
    public boolean lifeLimitEnabled() {
        return lifeLimit > 0;
    }
}
