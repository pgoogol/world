package com.pgoogol.npcworld.core;

import java.util.List;

/**
 * What an NPC perceives: the 3x3 cells around it, row by row (the centre is its own cell),
 * and its raw needs. Encoding them is up to the brain.
 */
public record Observation(List<CellType> surroundings, int hunger, int thirst, int energy) {

    public Observation {
        surroundings = List.copyOf(surroundings);
    }
}
