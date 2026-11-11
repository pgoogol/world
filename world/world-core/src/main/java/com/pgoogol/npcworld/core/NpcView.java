package com.pgoogol.npcworld.core;

import java.util.Optional;

/**
 * One NPC in a brain request: the current observation plus the outcome of the previous action.
 * After death or truncation {@code observation} already shows the reset NPC; {@code finalObservation}
 * is present only when {@code truncated} and holds the state right before the reset.
 */
public record NpcView(
        int npcId,
        Observation observation,
        double reward,
        boolean died,
        boolean truncated,
        Optional<Observation> finalObservation) {
}
