package com.pgoogol.npcworld.core;

/** Mutable NPC state, owned by the world. */
final class Npc {

    final int id;
    Position position;
    int hunger;
    int thirst;
    int energy;
    int age;          // ticks since the last collapse or truncation
    long totalAge;    // ticks since birth; a collapse does not reset it
    long lifespan;    // dies of old age when totalAge reaches it; unused when ageing is off
    int generation;   // 0 for founders, one more than the NPC it replaced for newborns
    int food;         // stage 3: carried food portions
    int wood;         // stage 3: carried wood

    // Outcome of the previous action, reported to the brain with the next view.
    double reward;
    boolean died;
    boolean truncated;
    Observation finalObservation;

    Npc(int id) {
        this.id = id;
    }

    NpcSnapshot snapshot() {
        return new NpcSnapshot(id, position, hunger, thirst, energy, age, totalAge, lifespan, generation, food, wood);
    }
}
