package com.pgoogol.npcworld.core;

import java.util.Optional;

/** A newborn took the place of an NPC that died of old age. It appeared next to {@code parentId}. */
public record NpcBorn(int npcId, long tick, int generation, Optional<Integer> parentId) {
}
