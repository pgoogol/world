package com.pgoogol.npcworld.core;

import java.util.List;

/**
 * What an NPC perceives: the square of cells around it, row by row (the centre is its own cell),
 * its raw needs and its own position. Encoding them is up to the brain.
 *
 * <p>Stage 3 adds what the NPC carries, the season with the ticks left in it (season 0 = no seasons)
 * and {@code hereAmount}: portions on a ripe field, food in a store or wood on a building site under the NPC.
 */
public record Observation(
        List<CellType> surroundings,
        int hunger,
        int thirst,
        int energy,
        Position position,
        int carriedFood,
        int carriedWood,
        int season,
        int seasonTicksLeft,
        int hereAmount) {

    public Observation {
        surroundings = List.copyOf(surroundings);
    }

    /** Stages 1 and 2: nothing carried, no seasons. */
    public Observation(List<CellType> surroundings, int hunger, int thirst, int energy, Position position) {
        this(surroundings, hunger, thirst, energy, position, 0, 0, 0, 0, 0);
    }

    /** Side of the surroundings square. */
    public int viewSize() {
        return (int) Math.round(Math.sqrt(surroundings.size()));
    }
}
