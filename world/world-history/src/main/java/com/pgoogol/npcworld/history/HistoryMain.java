package com.pgoogol.npcworld.history;

import com.pgoogol.npcworld.app.AppConfig;
import com.pgoogol.npcworld.app.Args;
import com.pgoogol.npcworld.app.ConfigLoader;
import com.pgoogol.npcworld.brain.GrpcBrain;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stage 3: runs the world with its chronicle in PostgreSQL, saves and resumes worlds, and answers
 * questions about a run. The brains server must be running for {@code run} and {@code resume}.
 *
 * <pre>
 * run    --config config/stage-3.yaml [--ticks N] [--save-every N]
 * resume --save ID [--ticks N] [--save-every N]
 * runs
 * npc    --run ID --npc ID
 * year   --run ID --year N            (years count from 0)
 * </pre>
 */
@SpringBootApplication
public class HistoryMain implements ApplicationRunner {

    private final Chronicle chronicle;

    public HistoryMain(Chronicle chronicle) {
        this.chronicle = chronicle;
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HistoryMain.class);
        // Arguments are commands for this tool, not Spring properties.
        application.setAddCommandLineProperties(false);
        application.run(args);
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        String[] raw = arguments.getSourceArgs();
        if (raw.length == 0) {
            System.out.println("Commands: run, resume, runs, npc, year (see HistoryMain)");
            return;
        }
        Args args = new Args(Arrays.copyOfRange(raw, 1, raw.length));
        switch (raw[0]) {
            case "run" -> {
                String configPath = args.get("config").orElse("config/stage-3.yaml");
                AppConfig config = ConfigLoader.load(Path.of(configPath));
                long runId = chronicle.startRun(configPath, config.world().seed(), config.world().seasons().seasonTicks());
                simulate(runId, config, new World(config.world()), args);
            }
            case "resume" -> {
                long saveId = Long.parseLong(args.get("save").orElseThrow(() -> new IllegalArgumentException("--save ID")));
                Chronicle.Save save = chronicle.load(saveId).orElseThrow(() -> new IllegalArgumentException("No save " + saveId));
                Chronicle.Run run = chronicle.run(save.runId()).orElseThrow();
                AppConfig config = ConfigLoader.load(Path.of(run.configPath()));
                System.out.printf("Resuming run %d from tick %d%n", run.id(), save.tick());
                chronicle.rewind(run.id(), save.tick());
                simulate(run.id(), config, World.restore(config.world(), save.state()), args);
            }
            case "runs" -> chronicle.runs().forEach(run -> System.out.printf("run %d: %s, seed %d%n",
                    run.id(), run.configPath(), run.seed()));
            case "npc" -> chronicle.npcHistory(id(args, "run"), (int) id(args, "npc"))
                    .forEach(event -> System.out.println(describe(event)));
            case "year" -> System.out.println(chronicle.year(id(args, "run"), id(args, "year")));
            default -> throw new IllegalArgumentException("Unknown command: " + raw[0]);
        }
    }

    private void simulate(long runId, AppConfig config, World world, Args args) throws Exception {
        long ticks = args.get("ticks").map(Long::parseLong).orElse(config.trainingTicks());
        long saveEvery = args.get("save-every").map(Long::parseLong).orElse(0L);
        try (GrpcBrain brain = GrpcBrain.connect(config.brainAddress());
                ChronicleWriter writer = new ChronicleWriter(chronicle, runId, 500)) {
            world.addListener(writer);
            Simulation simulation = new Simulation(world, brain);
            long start = System.nanoTime();
            for (long i = 0; i < ticks && world.population() > 0; i++) {
                simulation.tick();
                if (saveEvery > 0 && world.tick() % saveEvery == 0) {
                    long saveId = chronicle.save(runId, world.exportState());
                    System.out.printf("Saved tick %d as save %d%n", world.tick(), saveId);
                }
            }
            double seconds = (System.nanoTime() - start) / 1e9;
            System.out.printf(Locale.ROOT, "Run %d: now at tick %d, population %d (%.1f s)%n",
                    runId, world.tick(), world.population(), seconds);
        }
    }

    private static long id(Args args, String name) {
        return Long.parseLong(args.get(name).orElseThrow(() -> new IllegalArgumentException("--" + name + " is required")));
    }

    private static String describe(ChronicleEvent event) {
        StringBuilder text = new StringBuilder("tick " + event.tick() + " " + event.kind());
        if (event.npcAge() != null) {
            text.append(", age ").append(event.npcAge());
        }
        if (event.value() != null) {
            text.append(", value ").append(event.value());
        }
        if (event.x() != null) {
            text.append(" at (").append(event.x()).append(", ").append(event.y()).append(')');
        }
        if (event.detail() != null) {
            text.append(", ").append(event.detail());
        }
        return text.toString();
    }
}
