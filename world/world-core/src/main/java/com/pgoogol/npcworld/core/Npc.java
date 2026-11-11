package com.pgoogol.npcworld.core;

/** Mutable NPC state, owned by the world. */
final class Npc {

    final int id;
    Position position;
    int hunger;
    int thirst;
    int energy;
    int age;

    // Outcome of the previous action, reported to the brain with the next view.
    double reward;
    boolean died;
    boolean truncated;
    Observation finalObservation;

    Npc(int id) {
        this.id = id;
    }

    NpcSnapshot snapshot() {
        return new NpcSnapshot(id, position, hunger, thirst, energy, age);
    }
}
