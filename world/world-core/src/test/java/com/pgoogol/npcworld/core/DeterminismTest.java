package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DeterminismTest {

    @Test
    void sameSeedGivesTheSameStateAfterAThousandTicks() {
        assertThat(run(42).snapshot).isEqualTo(run(42).snapshot);
        assertThat(run(42).lives).isEqualTo(run(42).lives);
    }

    @Test
    void differentSeedGivesADifferentRun() {
        assertThat(run(42).snapshot).isNotEqualTo(run(43).snapshot);
    }

    @Test
    void randomWalkersDieWithinAThousandTicks() {
        // Guards the test above: a run without deaths would not exercise respawns.
        assertThat(run(42).lives).isNotEmpty();
    }

    private record Result(WorldSnapshot snapshot, List<LifeEnded> lives) {
    }

    private static Result run(long seed) {
        World world = new World(TestConfigs.stage1().seed(seed).build());
        List<LifeEnded> lives = new ArrayList<>();
        world.addListener(lives::add);
        Random choices = new Random(seed);
        Brain brain = (tick, views) -> {
            Map<Integer, Action> actions = new HashMap<>();
            for (NpcView view : views) {
                actions.put(view.npcId(), Action.values()[choices.nextInt(Action.values().length)]);
            }
            return actions;
        };

        new Simulation(world, brain).run(1000);

        return new Result(world.snapshot(), lives);
    }
}
