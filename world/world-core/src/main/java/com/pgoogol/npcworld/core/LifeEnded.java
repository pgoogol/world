package com.pgoogol.npcworld.core;

/** An NPC's life ended at {@code tick} after {@code lifeLength} ticks. */
public record LifeEnded(int npcId, long tick, int lifeLength, Cause cause) {

    public enum Cause {
        DEATH,
        LIFE_LIMIT
    }
}
