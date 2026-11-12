package com.pgoogol.npcworld.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Draws a board from the seed. Shares are counted from the cells inside the wall.
 * A layout is accepted only when all safe cells (everything except lava and walls) are connected,
 * so every food and water cell can be reached without stepping on lava.
 */
final class BoardGenerator {

    private static final int MAX_ATTEMPTS = 1000;

    private BoardGenerator() {
    }

    static Board generate(WorldConfig config, Random random) {
        int inside = (config.width() - 2) * (config.height() - 2);
        int food = (int) Math.round(inside * config.foodShare());
        int water = (int) Math.round(inside * config.waterShare());
        int lava = (int) Math.round(inside * config.lavaShare());
        int trees = (int) Math.round(inside * config.economy().treeShare());

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Board board = new Board(config.width(), config.height());
            List<Position> free = new ArrayList<>(inside);
            for (int y = 1; y < config.height() - 1; y++) {
                for (int x = 1; x < config.width() - 1; x++) {
                    free.add(new Position(x, y));
                }
            }
            Collections.shuffle(free, random);
            int next = 0;
            for (int i = 0; i < food; i++) {
                board.set(free.get(next++), CellType.FOOD);
            }
            for (int i = 0; i < water; i++) {
                board.set(free.get(next++), CellType.WATER);
            }
            for (int i = 0; i < lava; i++) {
                board.set(free.get(next++), CellType.LAVA);
            }
            // Stage 3. Trees come last, so the earlier stages draw exactly the same boards as before.
            for (int i = 0; i < trees; i++) {
                board.set(free.get(next++), CellType.TREE);
            }
            if (safeCellsAreConnected(board)) {
                return board;
            }
        }
        throw new IllegalStateException("No board with connected safe cells after " + MAX_ATTEMPTS + " attempts");
    }

    static boolean safeCellsAreConnected(Board board) {
        int safeCells = 0;
        Position start = null;
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                if (board.isSafe(x, y)) {
                    safeCells++;
                    if (start == null) {
                        start = new Position(x, y);
                    }
                }
            }
        }
        if (start == null) {
            return false;
        }

        boolean[] seen = new boolean[board.width() * board.height()];
        Deque<Position> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start.y() * board.width() + start.x()] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            Position current = queue.poll();
            reached++;
            for (Action step : List.of(Action.UP, Action.DOWN, Action.LEFT, Action.RIGHT)) {
                Position next = current.moved(step);
                int index = next.y() * board.width() + next.x();
                if (board.isSafe(next.x(), next.y()) && !seen[index]) {
                    seen[index] = true;
                    queue.add(next);
                }
            }
        }
        return reached == safeCells;
    }
}
