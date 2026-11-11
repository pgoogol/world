package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.brain.GrpcBrain;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import java.nio.file.Path;

/**
 * Headless run at full speed against a running brains server.
 *
 * <p>Arguments: {@code --config <path>} (default {@code config/stage-1.yaml}),
 * {@code --ticks <n>} (default: {@code training.ticks} from the config).
 */
public final class TrainingMain {

    private TrainingMain() {
    }

    public static void main(String[] rawArgs) throws Exception {
        Args args = new Args(rawArgs);
        AppConfig config = ConfigLoader.load(Path.of(args.get("config").orElse("config/stage-1.yaml")));
        long ticks = args.get("ticks").map(Long::parseLong).orElse(config.trainingTicks());

        try (GrpcBrain brain = GrpcBrain.connect(config.brainAddress())) {
            Simulation simulation = new Simulation(new World(config.world()), brain);
            long start = System.nanoTime();
            simulation.run(ticks);
            double seconds = (System.nanoTime() - start) / 1e9;
            System.out.printf("Ran %d ticks in %.1f s (%.0f ticks/s)%n", ticks, seconds, ticks / seconds);
        }
    }
}
