package com.pgoogol.npcworld.core;

import java.util.List;
import java.util.Optional;

/**
 * One NPC in a brain request: the current observation plus the outcome of the previous action.
 * After a collapse or truncation {@code observation} already shows the reset NPC; {@code finalObservation}
 * is present only when {@code truncated} and holds the state right before the reset.
 * {@code neighbours} lists the NPCs standing in the 3x3 around this one.
 */
public record NpcView(
        int npcId,
        Observation observation,
        double reward,
        boolean died,
        boolean truncated,
        Optional<Observation> finalObservation,
        List<Integer> neighbours) {

    public NpcView {
        neighbours = List.copyOf(neighbours);
    }
}
