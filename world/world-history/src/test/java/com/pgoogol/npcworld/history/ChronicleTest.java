package com.pgoogol.npcworld.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.app.ConfigLoader;
import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.Brain;
import com.pgoogol.npcworld.core.BuildingCompleted;
import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.FieldSown;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import com.pgoogol.npcworld.core.NpcView;
import com.pgoogol.npcworld.core.Position;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.SeasonChanged;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import com.pgoogol.npcworld.core.WorldConfig;
import com.pgoogol.npcworld.core.WorldListener;
import com.pgoogol.npcworld.core.WorldState;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Step 3.8: the chronicle and saved worlds in a real PostgreSQL (Testcontainers; skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class ChronicleTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final Path STAGE_3 = Path.of("..", "..", "config", "stage-3.yaml");

    private static Chronicle chronicle;

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        chronicle = new Chronicle(JdbcClient.create(dataSource), new JdbcTemplate(dataSource));
    }

    @Test
    void writerStoresEveryEventAndTheHistoryOfOneNpcComesBackInOrder() throws Exception {
        long run = chronicle.startRun("config/test.yaml", 42, 500);
        try (ChronicleWriter writer = new ChronicleWriter(chronicle, run, 2)) {
            writer.onNpcBorn(new NpcBorn(7, 100, 1, Optional.of(3)));
            writer.onFieldSown(new FieldSown(150, 7, new Position(4, 5)));
            writer.onBuildingCompleted(new BuildingCompleted(180, 7, new Position(5, 5), CellType.STORE));
            writer.onLifeEnded(new LifeEnded(8, 190, 40, LifeEnded.Cause.DEATH, 0, 40));
            writer.onLifeEnded(new LifeEnded(7, 400, 300, LifeEnded.Cause.DEATH, 1, 300));
        }

        List<ChronicleEvent> history = chronicle.npcHistory(run, 7);

        assertThat(history).extracting(ChronicleEvent::kind).containsExactly("born", "sown", "built", "death");
        assertThat(history.getFirst().value()).isEqualTo(3L);
        assertThat(history.get(1).x()).isEqualTo(4);
        assertThat(history.get(2).detail()).isEqualTo("store");
        assertThat(history.getLast().npcAge()).isEqualTo(300L);
    }

    @Test
    void yearSummaryCountsWhatHappenedInThatYearOnly() throws Exception {
        long run = chronicle.startRun("config/test.yaml", 42, 500);
        try (ChronicleWriter writer = new ChronicleWriter(chronicle, run, 100)) {
            writer.onNpcBorn(new NpcBorn(1, 1999, 1, Optional.empty()));
            writer.onNpcBorn(new NpcBorn(2, 2000, 1, Optional.empty()));
            writer.onNpcBorn(new NpcBorn(3, 3999, 1, Optional.empty()));
            writer.onNpcRetired(new NpcRetired(4, 2500, 6000, 0));
            writer.onLifeEnded(new LifeEnded(5, 2600, 10, LifeEnded.Cause.DEATH, 0, 10));
            writer.onBuildingCompleted(new BuildingCompleted(2700, 1, new Position(1, 1), CellType.SHELTER));
            writer.onSeasonChanged(new SeasonChanged(2000, Season.SPRING, 1));
        }

        YearSummary second = chronicle.year(run, 1);

        assertThat(second).isEqualTo(new YearSummary(1, 2000, 4000, 2, 1, 1, 0, 0, 1, 0));
    }

    @Test
    void aSavedWorldComesBackFromTheDatabaseUnchangedAndContinuesTheSame() throws Exception {
        WorldConfig config = ConfigLoader.load(STAGE_3).world();
        World original = new World(config);
        Brain brain = new HashBrain();
        Simulation simulation = new Simulation(original, brain);
        simulation.run(300);
        long run = chronicle.startRun(STAGE_3.toString(), config.seed(), config.seasons().seasonTicks());

        long saveId = chronicle.save(run, original.exportState());
        Chronicle.Save save = chronicle.load(saveId).orElseThrow();
        World restored = World.restore(config, save.state());
        simulation.run(300);
        new Simulation(restored, brain).run(300);

        assertThat(save.tick()).isEqualTo(300);
        assertThat(save.runId()).isEqualTo(run);
        assertThat(restored.exportState()).isEqualTo(original.exportState());
    }

    @Test
    void rewindingToASaveForgetsWhatCameAfterIt() throws Exception {
        long run = chronicle.startRun("config/test.yaml", 42, 500);
        try (ChronicleWriter writer = new ChronicleWriter(chronicle, run, 10)) {
            writer.onNpcBorn(new NpcBorn(1, 100, 1, Optional.empty()));
            writer.onLifeEnded(new LifeEnded(1, 200, 100, LifeEnded.Cause.DEATH, 1, 100));
        }
        WorldConfig config = ConfigLoader.load(STAGE_3).world();
        World world = new World(config);
        new Simulation(world, new HashBrain()).run(150);
        long kept = chronicle.save(run, world.exportState());
        new Simulation(world, new HashBrain()).run(100);
        long dropped = chronicle.save(run, world.exportState());

        chronicle.rewind(run, 150);

        assertThat(chronicle.npcHistory(run, 1)).extracting(ChronicleEvent::kind).containsExactly("born");
        assertThat(chronicle.load(kept)).isPresent();
        assertThat(chronicle.load(dropped)).isEmpty();
    }

    @Test
    void aSimulationWritesItsWholeChronicle() throws Exception {
        WorldConfig config = ConfigLoader.load(STAGE_3).world();
        World world = new World(config);
        long run = chronicle.startRun(STAGE_3.toString(), config.seed(), config.seasons().seasonTicks());
        EventCounter counter = new EventCounter();
        world.addListener(counter);
        try (ChronicleWriter writer = new ChronicleWriter(chronicle, run, 50)) {
            world.addListener(writer);
            new Simulation(world, new HashBrain()).run(2100);
        }

        Integer stored = JdbcClient.create(dataSource()).sql("select count(*) from event where run_id = ?")
                .param(run).query(Integer.class).single();
        long seasons = JdbcClient.create(dataSource()).sql("select count(*) from event where run_id = ? and kind = 'season'")
                .param(run).query(Long.class).single();

        assertThat(stored).isEqualTo(counter.events);
        assertThat(seasons).isEqualTo(4);
        assertThat(chronicle.year(run, 0).deaths()).isPositive();
    }

    private static PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    /** Decides from the tick and the NPC alone, so two worlds in the same state get the same moves. */
    private static final class HashBrain implements Brain {
        private static final Action[] ACTIONS = Action.values();

        @Override
        public Map<Integer, Action> decide(long tick, List<NpcView> views) {
            Map<Integer, Action> actions = new HashMap<>();
            for (NpcView view : views) {
                actions.put(view.npcId(), ACTIONS[new Random(tick * 1_000_003 + view.npcId()).nextInt(ACTIONS.length)]);
            }
            return actions;
        }
    }

    private static final class EventCounter implements WorldListener {
        int events;

        @Override
        public void onLifeEnded(LifeEnded event) {
            events++;
        }

        @Override
        public void onNpcBorn(NpcBorn event) {
            events++;
        }

        @Override
        public void onNpcRetired(NpcRetired event) {
            events++;
        }

        @Override
        public void onSeasonChanged(SeasonChanged event) {
            events++;
        }

        @Override
        public void onFieldSown(FieldSown event) {
            events++;
        }

        @Override
        public void onFieldHarvested(com.pgoogol.npcworld.core.FieldHarvested event) {
            events++;
        }

        @Override
        public void onBuildingCompleted(BuildingCompleted event) {
            events++;
        }
    }
}
