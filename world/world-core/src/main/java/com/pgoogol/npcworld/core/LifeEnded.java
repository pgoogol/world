package com.pgoogol.npcworld.core;

/**
 * An episode of an NPC life ended at {@code tick} after {@code lifeLength} ticks: the NPC collapsed
 * or reached the life limit. {@code npcAge} is its age in ticks since birth at that moment.
 */
public record LifeEnded(int npcId, long tick, int lifeLength, Cause cause, int generation, long npcAge) {

    public enum Cause {
        DEATH,
        LIFE_LIMIT
    }
}
