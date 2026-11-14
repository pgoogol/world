package com.pgoogol.npcworld.core;

import java.util.List;

/**
 * Stage 3: the complete state of a world, as plain data. A world restored from it continues exactly
 * as the original would, given the same brain decisions. Listeners and the configuration are not part
 * of it: the configuration comes with the restore call.
 *
 * <p>Cells are stored row by row. {@code timers} holds growth points left (-1 = nothing grows),
 * {@code growsInto} the ordinal of the cell type a timer leads to (-1 = none). {@code departed} are
 * NPCs that died for good in the last tick and still show up once, with the neighbours they had then.
 * {@code random} is the state of the world's random generator, Base64.
 */
public record WorldState(
        long tick,
        int nextNpcId,
        long lastBirthTick,
        List<CellType> cells,
        List<Integer> timers,
        List<Integer> growsInto,
        List<Integer> amounts,
        List<Integer> work,
        List<NpcState> npcs,
        List<NpcState> departed,
        List<List<Integer>> departedNeighbours,
        List<Integer> retired,
        List<Integer> retiringNext,
        String random) {

    public WorldState {
        cells = List.copyOf(cells);
        timers = List.copyOf(timers);
        growsInto = List.copyOf(growsInto);
        amounts = List.copyOf(amounts);
        work = List.copyOf(work);
        npcs = List.copyOf(npcs);
        departed = List.copyOf(departed);
        departedNeighbours = departedNeighbours.stream().map(List::copyOf).toList();
        retired = List.copyOf(retired);
        retiringNext = List.copyOf(retiringNext);
    }
}
