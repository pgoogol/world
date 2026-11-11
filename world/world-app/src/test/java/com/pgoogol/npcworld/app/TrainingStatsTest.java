package com.pgoogol.npcworld.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.Brain;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcView;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrainingStatsTest {

    private static final Path STAGE_1 = Path.of("..", "..", "config", "stage-1.yaml");

    @Test
    void windowHoldsTheLivesThatEndedInIt() {
        LifeStats stats = new LifeStats(1000);
        stats.onLifeEnded(new LifeEnded(0, 300, 100, LifeEnded.Cause.DEATH, 0, 300));
        stats.onLifeEnded(new LifeEnded(1, 900, 205, LifeEnded.Cause.LIFE_LIMIT, 0, 900));

        assertThat(stats.closeWindowIfDue(999)).isEmpty();
        LifeStats.Row row = stats.closeWindowIfDue(1000).orElseThrow();

        assertThat(row).isEqualTo(new LifeStats.Row(1000, 2, 305));
        assertThat(row.meanLifeLength()).isEqualTo(152.5);
        assertThat(row.toCsv()).isEqualTo("1000,2,152.50");
    }

    @Test
    void nextWindowStartsEmptyAndTotalsKeepCounting() {
        LifeStats stats = new LifeStats(1000);
        stats.onLifeEnded(new LifeEnded(0, 300, 100, LifeEnded.Cause.DEATH, 0, 300));
        stats.closeWindowIfDue(1000);

        LifeStats.Row empty = stats.closeWindowIfDue(2000).orElseThrow();

        assertThat(empty.livesEnded()).isZero();
        assertThat(empty.meanLifeLength()).isNaN();
        assertThat(empty.toCsv()).isEqualTo("2000,0,");
        assertThat(stats.totalLives()).isEqualTo(1);
        assertThat(stats.overallMeanLifeLength()).isEqualTo(100.0);
    }

    @Test
    void trainingRunWritesOneRowPerWindow(@TempDir Path directory) throws Exception {
        Path out = directory.resolve("nested").resolve("stats.csv");

        runWithRandomBrain(out, 3000);

        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(4);
        assertThat(lines.getFirst()).isEqualTo("tick,lives_ended,mean_life_length");
        assertThat(lines.get(1)).startsWith("1000,");
        assertThat(lines.get(3)).startsWith("3000,");
    }

    @Test
    void sameSeedGivesAnIdenticalCsv(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("first.csv");
        Path second = directory.resolve("second.csv");

        runWithRandomBrain(first, 5000);
        runWithRandomBrain(second, 5000);

        assertThat(Files.readString(first)).isEqualTo(Files.readString(second));
    }

    private static void runWithRandomBrain(Path out, long ticks) throws Exception {
        AppConfig config = ConfigLoader.load(STAGE_1);
        World world = new World(config.world());
        LifeStats stats = new LifeStats(config.statsWindow());
        world.addListener(stats);
        Random choices = new Random(config.world().seed());
        Brain brain = (tick, views) -> {
            Map<Integer, Action> actions = new HashMap<>();
            for (NpcView view : views) {
                actions.put(view.npcId(), Action.values()[choices.nextInt(Action.values().length)]);
            }
            return actions;
        };

        TrainingMain.run(new Simulation(world, brain), stats, ticks, out);
    }
}
