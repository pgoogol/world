package com.pgoogol.npcworld.core;

/** Stage 3: an NPC sowed a field; the world shows it from {@code tick}. */
public record FieldSown(long tick, int npcId, Position position) {
}
