package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.brain.GrpcBrain;
import com.pgoogol.npcworld.core.Brain;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import com.pgoogol.npcworld.core.WorldConfig;
import com.pgoogol.npcworld.core.WorldState;
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
 * {@code --episodes <file.csv>} (optional: one row per life episode, birth and death of old age),
 * {@code --settlement <file.csv>} (optional, stage 3: population, stores and buildings per window),
 * {@code --brain local} (optional, step 3.7: random moves inside this process, without the brains server),
 * {@code --episode-ticks <n>} (optional, step 3.11: training in episodes) with
 * {@code --curve <file.csv>} (one row per episode) and optionally {@code --episode-start-tick <n>}: the first
 * world runs from tick 0, every later one starts from its state at tick {@code n}.
 *
 * <p>A run stops early when the settlement dies out. In episodes it does not: when the settlement
 * dies out or the episode reaches its length, a new world starts from the same configuration and the
 * run goes on until all its ticks are used.
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
        Path settlementOut = args.get("settlement").map(Path::of).orElse(null);

        long episodeTicks = args.get("episode-ticks").map(Long::parseLong).orElse(0L);
        long episodeStartTick = args.get("episode-start-tick").map(Long::parseLong).orElse(0L);
        Path curve = args.get("curve").map(Path::of).orElse(null);
        boolean local = args.get("brain").map("local"::equals).orElse(false);
        if (episodeTicks > 0 && (episodes != null || settlementOut != null)) {
            throw new IllegalArgumentException("--episode-ticks does not go with --episodes or --settlement");
        }

        try (GrpcBrain remote = local ? null : GrpcBrain.connect(config.brainAddress());
                EpisodeLog episodeLog = episodes != null ? new EpisodeLog(episodes) : null) {
            Brain brain = local ? new LocalRandomBrain(config.world().seed()) : remote;
            if (episodeTicks > 0) {
                LifeStats stats = new LifeStats(config.statsWindow());
                long start = System.nanoTime();
                int count = runEpisodes(config.world(), brain, stats, ticks, episodeTicks, episodeStartTick, out,
                        curve != null ? curve : out.resolveSibling("curve.csv"));
                double seconds = (System.nanoTime() - start) / 1e9;
                System.out.printf(Locale.ROOT, "Ran %d ticks in %.1f s (%.0f ticks/s), %d episodes%n",
                        ticks, seconds, ticks / seconds, count);
                return;
            }
            World world = new World(config.world());
            LifeStats stats = new LifeStats(config.statsWindow());
            world.addListener(stats);
            if (episodeLog != null) {
                world.addListener(episodeLog);
            }
            SettlementStats settlement = null;
            if (settlementOut != null) {
                settlement = new SettlementStats(config.world().seasons());
                world.addListener(settlement);
            }
            Simulation simulation = new Simulation(world, brain);

            long start = System.nanoTime();
            long ran = run(simulation, stats, ticks, out, settlement, settlementOut);
            double seconds = (System.nanoTime() - start) / 1e9;

            System.out.printf(Locale.ROOT, "Ran %d ticks in %.1f s (%.0f ticks/s)%n", ran, seconds, ran / seconds);
            if (world.population() == 0) {
                System.out.printf(Locale.ROOT, "The settlement died out at tick %d%n", world.tick());
            }
            System.out.printf(Locale.ROOT, "Lives ended: %d, mean life length: %.1f ticks%n",
                    stats.totalLives(), stats.overallMeanLifeLength());
            System.out.println("Statistics: " + out.toAbsolutePath());
        }
    }

    /** Runs the simulation and writes one CSV row per closed statistics window. */
    static void run(Simulation simulation, LifeStats stats, long ticks, Path out) throws IOException {
        run(simulation, stats, ticks, out, null, null);
    }

    /**
     * Runs the simulation, writes one row per closed statistics window to {@code out} and, with
     * {@code settlement} set, to {@code settlementOut}. Stops when the settlement dies out.
     *
     * @return the ticks actually run
     */
    static long run(Simulation simulation, LifeStats stats, long ticks, Path out,
            SettlementStats settlement, Path settlementOut) throws IOException {
        createParent(out);
        if (settlementOut != null) {
            createParent(settlementOut);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(out);
                BufferedWriter settlementWriter = settlementOut != null ? Files.newBufferedWriter(settlementOut) : null) {
            writer.write(LifeStats.CSV_HEADER);
            writer.write('\n');
            if (settlementWriter != null) {
                settlementWriter.write(SettlementStats.CSV_HEADER);
                settlementWriter.write('\n');
            }
            World world = simulation.world();
            for (long i = 0; i < ticks; i++) {
                simulation.tick();
                Optional<LifeStats.Row> row = stats.closeWindowIfDue(world.tick());
                boolean extinct = world.population() == 0;
                if (row.isPresent()) {
                    writer.write(row.get().toCsv());
                    writer.write('\n');
                }
                if (settlementWriter != null && (row.isPresent() || extinct)) {
                    settlementWriter.write(settlement.closeWindow(world.snapshot()));
                    settlementWriter.write('\n');
                }
                if (extinct) {
                    return i + 1;
                }
            }
            return ticks;
        }
    }

    /**
     * Step 3.11: runs world after world until {@code ticks} are used. An episode ends when the settlement
     * dies out or when its clock reaches {@code episodeTicks}. Life statistics go to {@code out} in windows
     * of the whole run, one row per episode goes to {@code curveOut}.
     *
     * <p>With {@code startTick > 0} the first world runs from tick 0 and its state at {@code startTick} is
     * kept; every later world is restored from it. The brain sees the clock jump back and starts over.
     *
     * @return the number of episodes, counting the last, unfinished one
     */
    static int runEpisodes(WorldConfig config, Brain brain, LifeStats stats, long ticks, long episodeTicks,
            long startTick, Path out, Path curveOut) throws IOException {
        int seasonTicks = config.seasons().seasonTicks();
        createParent(out);
        createParent(curveOut);
        try (BufferedWriter writer = Files.newBufferedWriter(out);
                BufferedWriter curveWriter = Files.newBufferedWriter(curveOut)) {
            writer.write(LifeStats.CSV_HEADER);
            writer.write('\n');
            curveWriter.write(EpisodeStats.CSV_HEADER);
            curveWriter.write('\n');
            long total = 0;
            int episode = 0;
            WorldState start = null;
            while (total < ticks) {
                World world = start != null ? World.restore(config, start) : new World(config);
                EpisodeStats episodeStats = new EpisodeStats();
                world.addListener(stats);
                world.addListener(episodeStats);
                Simulation simulation = new Simulation(world, brain);
                while (total < ticks && world.tick() < episodeTicks && world.population() > 0) {
                    simulation.tick();
                    total++;
                    if (start == null && startTick > 0 && world.tick() == startTick) {
                        start = world.exportState();
                    }
                    if (seasonTicks > 0 && world.tick() % seasonTicks == 0) {
                        episodeStats.seasonStarted(world.snapshot());
                    }
                    Optional<LifeStats.Row> row = stats.closeWindowIfDue(total);
                    if (row.isPresent()) {
                        writer.write(row.get().toCsv());
                        writer.write('\n');
                    }
                }
                curveWriter.write(episodeStats.close(episode, total, world.tick(), world.population()));
                curveWriter.write('\n');
                curveWriter.flush();
                episode++;
            }
            return episode;
        }
    }

    private static void createParent(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
    }
}
