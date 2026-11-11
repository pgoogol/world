package com.pgoogol.npcworld.core;

import java.util.List;
import java.util.Map;

/** Drives the clock: one tick is one brain call for the whole group, then one world step. */
public final class Simulation {

    private final World world;
    private final Brain brain;

    public Simulation(World world, Brain brain) {
        this.world = world;
        this.brain = brain;
    }

    public void tick() {
        List<Integer> retired = world.drainRetired();
        if (!retired.isEmpty()) {
            brain.npcsRetired(retired);
        }
        List<NpcView> views = world.views();
        Map<Integer, Action> actions = brain.decide(world.tick(), views);
        world.step(actions);
    }

    public void run(long ticks) {
        for (long i = 0; i < ticks; i++) {
            tick();
        }
    }

    public World world() {
        return world;
    }
}
