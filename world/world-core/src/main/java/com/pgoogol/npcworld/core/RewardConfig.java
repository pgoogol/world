package com.pgoogol.npcworld.core;

/** Rewards computed by the world. {@code winterSurvived} (stage 3) goes to every NPC alive on the first day of spring. */
public record RewardConfig(double perTick, double consume, int consumeNeedMin, double death, double winterSurvived) {

    /** Stages 1 and 2: no winter. */
    public RewardConfig(double perTick, double consume, int consumeNeedMin, double death) {
        this(perTick, consume, consumeNeedMin, death, 0);
    }
}
