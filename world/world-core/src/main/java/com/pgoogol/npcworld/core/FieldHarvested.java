package com.pgoogol.npcworld.core;

/** Stage 3: an NPC took the last portion from a ripe field; the world shows it empty from {@code tick}. */
public record FieldHarvested(long tick, int npcId, Position position) {
}
