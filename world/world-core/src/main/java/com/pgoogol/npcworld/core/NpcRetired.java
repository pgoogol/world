package com.pgoogol.npcworld.core;

/** An NPC died of old age at {@code tick}, {@code totalAge} ticks after its birth. */
public record NpcRetired(int npcId, long tick, long totalAge, int generation) {
}
