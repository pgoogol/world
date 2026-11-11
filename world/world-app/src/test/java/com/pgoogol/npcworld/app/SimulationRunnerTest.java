package com.pgoogol.npcworld.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.Brain;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class SimulationRunnerTest {

    private static final Path STAGE_1 = Path.of("..", "..", "config", "stage-1.yaml");
    private static final Brain STAY = (tick, views) -> {
        Map<Integer, Action> actions = new HashMap<>();
        views.forEach(view -> actions.put(view.npcId(), Action.STAY));
        return actions;
    };

    @Test
    void publishesASnapshotAfterEveryTick() throws Exception {
        try (SimulationRunner runner = new SimulationRunner(simulation(STAY), 1000)) {
            assertThat(runner.latest().tick()).isZero();

            runner.start();

            waitFor(() -> runner.latest().tick() >= 5);
            assertThat(runner.failure()).isEmpty();
        }
    }

    @Test
    void stopsAndReportsWhenTheBrainFails() throws Exception {
        Brain broken = (tick, views) -> {
            if (tick == 3) {
                throw new IllegalStateException("brain is gone");
            }
            return STAY.decide(tick, views);
        };
        try (SimulationRunner runner = new SimulationRunner(simulation(broken), 1000)) {
            runner.start();

            waitFor(() -> runner.failure().isPresent());
            assertThat(runner.failure().get()).hasMessage("brain is gone");
            assertThat(runner.latest().tick()).isEqualTo(3);
        }
    }

    private static Simulation simulation(Brain brain) throws Exception {
        return new Simulation(new World(ConfigLoader.load(STAGE_1).world()), brain);
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition met within 5 s").isLessThan(deadline);
            Thread.sleep(5);
        }
    }
}
