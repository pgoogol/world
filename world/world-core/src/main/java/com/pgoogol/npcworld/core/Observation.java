package com.pgoogol.npcworld.core;

import java.util.List;

/**
 * What an NPC perceives: the 3x3 cells around it, row by row (the centre is its own cell),
 * its raw needs and its own position. Encoding them is up to the brain.
 */
public record Observation(List<CellType> surroundings, int hunger, int thirst, int energy, Position position) {

    public Observation {
        surroundings = List.copyOf(surroundings);
    }
}
