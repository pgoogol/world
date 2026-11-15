package com.pgoogol.npcworld.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.BuildingCompleted;
import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.FieldHarvested;
import com.pgoogol.npcworld.core.FieldSown;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Position;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.WorldSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpisodeStatsTest {

    private static final Path STAGE_3 = Path.of("..", "..", "config", "stage-3.yaml");

    @Test
    void rowCountsWhatTheSettlementDidInTheEpisode() {
        EpisodeStats stats = new EpisodeStats();
        Position place = new Position(1, 1);
        stats.onFieldSown(new FieldSown(10, 1, place));
        stats.onFieldSown(new FieldSown(11, 1, place));
        stats.onFieldHarvested(new FieldHarvested(400, 2, place));
        stats.onBuildingCompleted(new BuildingCompleted(500, 1, place, CellType.SHELTER));
        stats.onBuildingCompleted(new BuildingCompleted(600, 1, place, CellType.STORE));
        stats.onNpcBorn(new NpcBorn(7, 700, 1, Optional.of(1)));
        stats.onLifeEnded(new LifeEnded(2, 800, 300, LifeEnded.Cause.DEATH, 0, 300));
        stats.seasonStarted(snapshot(1500, Season.WINTER, 2));
        stats.seasonStarted(snapshot(2000, Season.SPRING, 1));

        String row = stats.close(3, 12345, 2100, 1);

        assertThat(EpisodeStats.CSV_HEADER.split(",")).hasSize(row.split(",").length);
        assertThat(row).isEqualTo("3,12345,2100,1,1,1,1,0,2,1,1,1,42,2");
    }

    @Test
    void winterNobodySurvivedDoesNotCount() {
        EpisodeStats stats = new EpisodeStats();
        stats.seasonStarted(snapshot(0, Season.SPRING, 2));
        stats.seasonStarted(snapshot(1500, Season.WINTER, 2));
        stats.seasonStarted(snapshot(2000, Season.SPRING, 0));

        assertThat(stats.close(0, 2000, 2000, 0)).startsWith("0,2000,2000,0,0,");
    }

    @Test
    void runInEpisodesStartsANewWorldWhenTheSettlementDiesOut(@TempDir Path directory) throws Exception {
        AppConfig config = ConfigLoader.load(STAGE_3);
        Path out = directory.resolve("life.csv");
        Path curve = directory.resolve("curve.csv");

        int episodes = TrainingMain.runEpisodes(config.world(), new LocalRandomBrain(config.world().seed()),
                new LifeStats(250), 3000, 10_000, 0, out, curve);

        List<String> rows = Files.readAllLines(curve);
        assertThat(episodes).isGreaterThan(1);
        assertThat(rows).hasSize(episodes + 1);
        assertThat(rows.getFirst()).isEqualTo(EpisodeStats.CSV_HEADER);
        assertThat(rows.getLast().split(",")[1]).isEqualTo("3000");
        // Life statistics keep counting across the worlds: one row per window of the whole run.
        assertThat(Files.readAllLines(out)).hasSize(1 + 3000 / 250);
    }

    @Test
    void episodeEndsAtItsLengthEvenWhenTheSettlementLives(@TempDir Path directory) throws Exception {
        AppConfig config = ConfigLoader.load(STAGE_3);
        Path curve = directory.resolve("curve.csv");

        TrainingMain.runEpisodes(config.world(), new LocalRandomBrain(config.world().seed()),
                new LifeStats(250), 100, 40, 0, directory.resolve("life.csv"), curve);

        List<String> rows = Files.readAllLines(curve);
        assertThat(rows).hasSize(4);
        assertThat(rows.get(1)).startsWith("0,40,40,");
        assertThat(rows.get(3)).startsWith("2,100,20,");
    }

    @Test
    void laterEpisodesStartFromTheStateAtTheStartTick(@TempDir Path directory) throws Exception {
        AppConfig config = ConfigLoader.load(STAGE_3);
        Path curve = directory.resolve("curve.csv");
        List<Long> firstTicks = new java.util.ArrayList<>();
        com.pgoogol.npcworld.core.Brain brain = new com.pgoogol.npcworld.core.Brain() {
            private final LocalRandomBrain random = new LocalRandomBrain(config.world().seed());
            private long last = -1;

            @Override
            public java.util.Map<Integer, com.pgoogol.npcworld.core.Action> decide(long tick,
                    List<com.pgoogol.npcworld.core.NpcView> views) {
                if (tick <= last) {
                    firstTicks.add(tick);
                }
                last = tick;
                return random.decide(tick, views);
            }
        };

        TrainingMain.runEpisodes(config.world(), brain, new LifeStats(250), 100, 40, 30, directory.resolve("life.csv"),
                curve);

        List<String> rows = Files.readAllLines(curve);
        // 40 ticks from tick 0, then episodes of 10 ticks from tick 30 to 40.
        assertThat(rows).hasSize(1 + 7);
        assertThat(rows.get(1)).startsWith("0,40,40,");
        assertThat(rows.get(2)).startsWith("1,50,40,");
        assertThat(firstTicks).containsOnly(30L).hasSize(6);
    }

    private static WorldSnapshot snapshot(long tick, Season season, int npcs) {
        NpcSnapshot npc = new NpcSnapshot(1, new Position(0, 0), 0, 0, 100, 0, 0, 0, 0, 0, 0);
        return new WorldSnapshot(tick, season, 2, 1, List.of(CellType.STORE, CellType.STORE), List.of(40, 2),
                java.util.Collections.nCopies(npcs, npc));
    }
}
