package com.pgoogol.npcworld.core;

/** Board coordinates: x grows to the right, y grows downwards. */
public record Position(int x, int y) {

    public Position moved(Action action) {
        return new Position(x + action.dx(), y + action.dy());
    }
}
