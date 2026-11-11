package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.brain.GrpcBrain;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Training mode: headless run at full speed against a running brains server,
 * with life statistics written to a CSV file.
 *
 * <p>Arguments: {@code --config <path>} (default {@code config/stage-1.yaml}),
 * {@code --ticks <n>} (default {@code training.ticks}),
 * {@code --out <file.csv>} (default {@code <training.out_dir>/training.csv}),
 * {@code --episodes <file.csv>} (optional: one row per life episode, birth and death of old age).
 */
public final class TrainingMain {

    private TrainingMain() {
    }

    public static void main(String[] rawArgs) throws Exception {
        Args args = new Args(rawArgs);
        AppConfig config = ConfigLoader.load(Path.of(args.get("config").orElse("config/stage-1.yaml")));
        long ticks = args.get("ticks").map(Long::parseLong).orElse(config.trainingTicks());
        Path out = Path.of(args.get("out").orElse(config.outDir() + "/training.csv"));

        Path episodes = args.get("episodes").map(Path::of).orElse(null);

        try (GrpcBrain brain = GrpcBrain.connect(config.brainAddress());
                EpisodeLog episodeLog = episodes != null ? new EpisodeLog(episodes) : null) {
            World world = new World(config.world());
            LifeStats stats = new LifeStats(config.statsWindow());
            world.addListener(stats);
            if (episodeLog != null) {
                world.addListener(episodeLog);
            }
            Simulation simulation = new Simulation(world, brain);

            long start = System.nanoTime();
            run(simulation, stats, ticks, out);
            double seconds = (System.nanoTime() - start) / 1e9;

            System.out.printf(Locale.ROOT, "Ran %d ticks in %.1f s (%.0f ticks/s)%n", ticks, seconds, ticks / seconds);
            System.out.printf(Locale.ROOT, "Lives ended: %d, mean life length: %.1f ticks%n",
                    stats.totalLives(), stats.overallMeanLifeLength());
            System.out.println("Statistics: " + out.toAbsolutePath());
        }
    }

    /** Runs the simulation and writes one CSV row per closed statistics window. */
    static void run(Simulation simulation, LifeStats stats, long ticks, Path out) throws IOException {
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(out)) {
            writer.write(LifeStats.CSV_HEADER);
            writer.write('\n');
            for (long i = 0; i < ticks; i++) {
                simulation.tick();
                Optional<LifeStats.Row> row = stats.closeWindowIfDue(simulation.world().tick());
                if (row.isPresent()) {
                    writer.write(row.get().toCsv());
                    writer.write('\n');
                }
            }
        }
    }
}
