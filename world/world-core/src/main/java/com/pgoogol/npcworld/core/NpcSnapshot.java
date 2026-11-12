package com.pgoogol.npcworld.core;

/**
 * {@code age} counts ticks since the last collapse, {@code totalAge} since birth.
 * {@code lifespan} is zero when nobody dies of old age.
 */
public record NpcSnapshot(
        int id, Position position, int hunger, int thirst, int energy, int age,
        long totalAge, long lifespan, int generation, int carriedFood, int carriedWood) {

    /** Stages 1 and 2: nothing carried. */
    public NpcSnapshot(
            int id, Position position, int hunger, int thirst, int energy, int age,
            long totalAge, long lifespan, int generation) {
        this(id, position, hunger, thirst, energy, age, totalAge, lifespan, generation, 0, 0);
    }
}
