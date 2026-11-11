package com.pgoogol.npcworld.core;

/** What an NPC can do in one tick. */
public enum Action {
    STAY(0, 0),
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    /** Eat or drink on the current cell. */
    CONSUME(0, 0);

    private final int dx;
    private final int dy;

    Action(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }
}
