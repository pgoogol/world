package com.pgoogol.npcworld.core;

/** What an NPC can do in one tick. */
public enum Action {
    STAY(0, 0),
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    /** Eat or drink on the current cell. */
    CONSUME(0, 0),
    // Stage 3: everything below acts on the current cell or on what the NPC carries.
    /** Pick up food from wild food or a ripe field, or wood from a tree. */
    GATHER(0, 0),
    /** Eat one carried food portion. */
    EAT_CARRIED(0, 0),
    /** Sow one carried food portion on an empty cell. */
    PLANT(0, 0),
    /** Start a shelter site on an empty cell, bring wood to it or work on it. */
    BUILD_SHELTER(0, 0),
    /** The same for a store. */
    BUILD_STORE(0, 0),
    /** Put all carried food into a store. */
    DEPOSIT(0, 0),
    /** Take one food portion from a store. */
    WITHDRAW(0, 0);

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
