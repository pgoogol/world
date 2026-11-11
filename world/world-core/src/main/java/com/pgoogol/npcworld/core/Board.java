package com.pgoogol.npcworld.core;

import java.util.Arrays;
import java.util.List;

/** Grid of cells surrounded by a wall. */
final class Board {

    private final int width;
    private final int height;
    private final CellType[] cells;

    Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new CellType[width * height];
        Arrays.fill(cells, CellType.EMPTY);
        for (int x = 0; x < width; x++) {
            set(x, 0, CellType.WALL);
            set(x, height - 1, CellType.WALL);
        }
        for (int y = 0; y < height; y++) {
            set(0, y, CellType.WALL);
            set(width - 1, y, CellType.WALL);
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    CellType get(int x, int y) {
        return cells[y * width + x];
    }

    CellType get(Position position) {
        return get(position.x(), position.y());
    }

    void set(int x, int y, CellType type) {
        cells[y * width + x] = type;
    }

    void set(Position position, CellType type) {
        set(position.x(), position.y(), type);
    }

    List<CellType> cells() {
        return List.of(cells);
    }
}
