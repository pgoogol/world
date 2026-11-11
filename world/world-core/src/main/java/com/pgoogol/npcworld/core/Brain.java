package com.pgoogol.npcworld.core;

import java.util.List;
import java.util.Map;

/**
 * Port to whatever decides for the NPCs. The world calls it once per tick for the whole group
 * and expects exactly one action per NPC id.
 */
public interface Brain {

    Map<Integer, Action> decide(long tick, List<NpcView> views);

    /** NPCs that died of old age: the brain should forget them. Called before the next {@code decide}. */
    default void npcsRetired(List<Integer> npcIds) {
    }
}
