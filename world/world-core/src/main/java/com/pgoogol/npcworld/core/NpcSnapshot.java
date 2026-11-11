package com.pgoogol.npcworld.core;

/**
 * {@code age} counts ticks since the last collapse, {@code totalAge} since birth.
 * {@code lifespan} is zero when nobody dies of old age.
 */
public record NpcSnapshot(
        int id, Position position, int hunger, int thirst, int energy, int age,
        long totalAge, long lifespan, int generation) {
}
