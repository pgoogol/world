package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.Brain;
import com.pgoogol.npcworld.core.NpcView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Step 3.7: random moves decided inside the world process. Runs without the brains server,
 * so a run with it measures the world alone, without gRPC and Python.
 */
public final class LocalRandomBrain implements Brain {

    private static final Action[] ACTIONS = Action.values();

    private final Random random;

    public LocalRandomBrain(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public Map<Integer, Action> decide(long tick, List<NpcView> views) {
        Map<Integer, Action> actions = new HashMap<>();
        for (NpcView view : views) {
            actions.put(view.npcId(), ACTIONS[random.nextInt(ACTIONS.length)]);
        }
        return actions;
    }
}
