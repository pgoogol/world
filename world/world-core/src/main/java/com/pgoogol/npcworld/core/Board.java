package com.pgoogol.npcworld.core;

import java.util.Arrays;
import java.util.List;

/**
 * Grid of cells surrounded by a wall.
 *
 * <p>Some cells change on their own over time: eaten wild food grows back, a stump grows back into
 * a tree, a sown field ripens. Each such cell has a growth timer counted in growth points (100 per
 * tick of full growth, see {@link SeasonConfig}) and the cell type it turns into. A timer only fires
 * while the cell still is what it was when the timer started; anything built over it cancels it.
 *
 * <p>{@code amount} holds the portions on a ripe field, the food in a store or the wood brought to a
 * building site; {@code work} holds the work done on a building site.
 */
final class Board {

    static final int FULL_GROWTH = 100;
    private static final int NO_TIMER = -1;

    private final int width;
    private final int height;
    private final CellType[] cells;
    private final int[] timer;
    private final CellType[] growsInto;
    private final int[] amount;
    private final int[] work;

    Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new CellType[width * height];
        this.timer = new int[width * height];
        this.growsInto = new CellType[width * height];
        this.amount = new int[width * height];
        this.work = new int[width * height];
        Arrays.fill(cells, CellType.EMPTY);
        Arrays.fill(timer, NO_TIMER);
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

    boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
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

    int amount(Position position) {
        return amount[index(position)];
    }

    void setAmount(Position position, int value) {
        amount[index(position)] = value;
    }

    int work(Position position) {
        return work[index(position)];
    }

    void setWork(Position position, int value) {
        work[index(position)] = value;
    }

    /**
     * Turns the cell into {@code now} and starts a timer: after {@code ticks} ticks of full growth
     * the cell becomes {@code later}, provided it is still {@code now} by then.
     */
    void startGrowth(Position position, CellType now, CellType later, int ticks) {
        int index = index(position);
        cells[index] = now;
        timer[index] = ticks * FULL_GROWTH;
        growsInto[index] = later;
    }

    /** Turns the cell into {@code type} and cancels any growth timer on it. */
    void replace(Position position, CellType type) {
        int index = index(position);
        cells[index] = type;
        timer[index] = NO_TIMER;
        growsInto[index] = null;
    }

    /** Removes the food from a cell and schedules its return after {@code regrowTicks} ticks of full growth. */
    void eatFood(Position position, int regrowTicks) {
        startGrowth(position, CellType.EMPTY, CellType.FOOD, regrowTicks);
    }

    /**
     * Advances every growth timer by {@code points} and applies the ones that finished.
     * {@code fieldYield} is put on every field that ripens.
     */
    void grow(int points, int fieldYield) {
        if (points <= 0) {
            return;
        }
        for (int index = 0; index < cells.length; index++) {
            if (timer[index] == NO_TIMER) {
                continue;
            }
            timer[index] -= points;
            if (timer[index] <= 0) {
                CellType next = growsInto[index];
                timer[index] = NO_TIMER;
                growsInto[index] = null;
                if (startedFrom(next) == cells[index]) {
                    cells[index] = next;
                    if (next == CellType.FIELD_RIPE) {
                        amount[index] = fieldYield;
                    }
                }
            }
        }
    }

    /**
     * Stage 3, first day of winter: wild food and unharvested ripe fields are gone. Wild food grows
     * back after {@code foodRegrowTicks} ticks of full growth, so in practice in spring.
     */
    void winterRot(int foodRegrowTicks) {
        for (int index = 0; index < cells.length; index++) {
            if (cells[index] == CellType.FOOD) {
                cells[index] = CellType.EMPTY;
                timer[index] = foodRegrowTicks * FULL_GROWTH;
                growsInto[index] = CellType.FOOD;
            } else if (cells[index] == CellType.FIELD_RIPE) {
                cells[index] = CellType.EMPTY;
                amount[index] = 0;
            }
        }
    }

    int count(CellType type) {
        int count = 0;
        for (CellType cell : cells) {
            if (cell == type) {
                count++;
            }
        }
        return count;
    }

    /** All cells of a type, row by row. */
    List<Position> positionsOf(CellType type) {
        List<Position> positions = new java.util.ArrayList<>();
        for (int index = 0; index < cells.length; index++) {
            if (cells[index] == type) {
                positions.add(new Position(index % width, index / width));
            }
        }
        return positions;
    }

    List<Integer> timers() {
        return Arrays.stream(timer).boxed().toList();
    }

    List<Integer> growsInto() {
        return Arrays.stream(growsInto).map(type -> type == null ? -1 : type.ordinal()).toList();
    }

    List<Integer> works() {
        return Arrays.stream(work).boxed().toList();
    }

    /** Stage 3: a board exactly as saved in a {@link WorldState}. */
    static Board restore(int width, int height, WorldState state) {
        int size = width * height;
        if (state.cells().size() != size || state.timers().size() != size || state.growsInto().size() != size
                || state.amounts().size() != size || state.work().size() != size) {
            throw new IllegalArgumentException("Saved board does not match " + width + "x" + height);
        }
        Board board = new Board(width, height);
        for (int index = 0; index < size; index++) {
            board.cells[index] = state.cells().get(index);
            board.timer[index] = state.timers().get(index);
            int next = state.growsInto().get(index);
            board.growsInto[index] = next < 0 ? null : CellType.values()[next];
            board.amount[index] = state.amounts().get(index);
            board.work[index] = state.work().get(index);
        }
        return board;
    }

    /** A cell an NPC can stand on without being hurt. */
    boolean isSafe(int x, int y) {
        CellType cell = get(x, y);
        return cell != CellType.WALL && cell != CellType.LAVA;
    }

    List<CellType> cells() {
        return List.of(cells);
    }

    List<Integer> amounts() {
        return Arrays.stream(amount).boxed().toList();
    }

    private int index(Position position) {
        return position.y() * width + position.x();
    }

    /** What a cell has to be while it grows into {@code next}. */
    private static CellType startedFrom(CellType next) {
        return switch (next) {
            case FOOD -> CellType.EMPTY;
            case TREE -> CellType.STUMP;
            case FIELD_RIPE -> CellType.FIELD_SOWN;
            default -> throw new IllegalStateException("Nothing grows into " + next);
        };
    }
}
