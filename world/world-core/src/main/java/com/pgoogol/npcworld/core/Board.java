package com.pgoogol.npcworld.core;

import java.util.Arrays;
import java.util.List;

/** Grid of cells surrounded by a wall. Eaten food leaves an empty cell that turns back into food later. */
final class Board {

    private static final long NO_REGROWTH = -1;

    private final int width;
    private final int height;
    private final CellType[] cells;
    private final long[] regrowAt;

    Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new CellType[width * height];
        this.regrowAt = new long[width * height];
        Arrays.fill(cells, CellType.EMPTY);
        Arrays.fill(regrowAt, NO_REGROWTH);
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

    /** Removes the food from a cell and schedules its return. */
    void eatFood(Position position, long regrowTick) {
        int index = position.y() * width + position.x();
        cells[index] = CellType.EMPTY;
        regrowAt[index] = regrowTick;
    }

    /** Puts food back on every cell whose regrowth tick has come. */
    void regrow(long tick) {
        for (int index = 0; index < cells.length; index++) {
            if (regrowAt[index] != NO_REGROWTH && regrowAt[index] <= tick) {
                cells[index] = CellType.FOOD;
                regrowAt[index] = NO_REGROWTH;
            }
        }
    }

    /** A cell an NPC can stand on without being hurt. */
    boolean isSafe(int x, int y) {
        CellType cell = get(x, y);
        return cell != CellType.WALL && cell != CellType.LAVA;
    }

    List<CellType> cells() {
        return List.of(cells);
    }
}
