package com.pgoogol.npcworld.core;

/** Everything about one NPC that a saved world needs to continue exactly where it stopped. */
public record NpcState(
        int id,
        Position position,
        int hunger,
        int thirst,
        int energy,
        int age,
        long totalAge,
        long lifespan,
        int generation,
        int food,
        int wood,
        double reward,
        boolean died,
        boolean truncated,
        Observation finalObservation) {

    static NpcState of(Npc npc) {
        return new NpcState(npc.id, npc.position, npc.hunger, npc.thirst, npc.energy, npc.age, npc.totalAge,
                npc.lifespan, npc.generation, npc.food, npc.wood, npc.reward, npc.died, npc.truncated,
                npc.finalObservation);
    }

    Npc toNpc() {
        Npc npc = new Npc(id);
        npc.position = position;
        npc.hunger = hunger;
        npc.thirst = thirst;
        npc.energy = energy;
        npc.age = age;
        npc.totalAge = totalAge;
        npc.lifespan = lifespan;
        npc.generation = generation;
        npc.food = food;
        npc.wood = wood;
        npc.reward = reward;
        npc.died = died;
        npc.truncated = truncated;
        npc.finalObservation = finalObservation;
        return npc;
    }
}
