package com.pgoogol.npcworld.core;

/** Receives world events. Listeners only observe: they must not change the world. */
public interface WorldListener {

    void onLifeEnded(LifeEnded event);
}
