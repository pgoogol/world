package com.pgoogol.npcworld.core;

/** Stage 3: an NPC finished a building of {@code type}; the world shows it from {@code tick}. */
public record BuildingCompleted(long tick, int npcId, Position position, CellType type) {
}
