package com.pgoogol.npcworld.core;

import java.util.List;

/**
 * Immutable copy of the world state. Cells are stored row by row; {@code amounts} holds, for the same
 * cells, the portions on a ripe field, the food in a store or the wood on a building site.
 * {@code season} is null without seasons. The view reads only this.
 */
public record WorldSnapshot(
        long tick, Season season, int width, int height, List<CellType> cells, List<Integer> amounts,
        List<NpcSnapshot> npcs) {

    public WorldSnapshot {
        cells = List.copyOf(cells);
        amounts = List.copyOf(amounts);
        npcs = List.copyOf(npcs);
    }

    public CellType cellAt(int x, int y) {
        return cells.get(y * width + x);
    }

    public int amountAt(int x, int y) {
        return amounts.get(y * width + x);
    }
}
