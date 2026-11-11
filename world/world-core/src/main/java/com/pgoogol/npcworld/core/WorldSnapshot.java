package com.pgoogol.npcworld.core;

import java.util.List;

/** Immutable copy of the world state. Cells are stored row by row. The view reads only this. */
public record WorldSnapshot(long tick, int width, int height, List<CellType> cells, List<NpcSnapshot> npcs) {

    public WorldSnapshot {
        cells = List.copyOf(cells);
        npcs = List.copyOf(npcs);
    }

    public CellType cellAt(int x, int y) {
        return cells.get(y * width + x);
    }
}
