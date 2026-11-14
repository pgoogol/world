package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Step 3.8: a restored world continues exactly as the original. */
class WorldSaveTest {

    @Test
    void aRestoredWorldContinuesExactlyLikeTheOriginal() {
        WorldConfig config = stageThree();
        World original = settlement(config);
        Brain brain = new HashBrain();
        Simulation simulation = new Simulation(original, brain);
        simulation.run(300);

        World restored = World.restore(config, original.exportState());
        simulation.run(400);
        new Simulation(restored, brain).run(400);

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        assertThat(restored.exportState()).isEqualTo(original.exportState());
        assertThat(restored.views()).isEqualTo(original.views());
    }

    @Test
    void theRunUsedForTheCheckHasBirthsDeathsAndWinters() {
        World world = settlement(stageThree());
        Counter counter = new Counter();
        world.addListener(counter);

        new Simulation(world, new HashBrain()).run(700);

        assertThat(counter.seasons).isGreaterThanOrEqualTo(4);
        assertThat(counter.deaths).isPositive();
        assertThat(counter.births).isPositive();
    }

    @Test
    void saveKeepsTheDeadWhoStillShowUpOnce() {
        WorldConfig config = TestConfigs.emptyBoard().size(5).npcs(2).lifeLimit(0)
                .population(new PopulationConfig(true, false, 0, 0, 0, 0, 0)).build();
        Board board = new Board(5, 5);
        board.set(2, 2, CellType.LAVA);
        World world = new World(config, board);
        world.npc(0).position = new Position(2, 2);
        world.npc(0).energy = 25;
        world.npc(1).position = new Position(2, 1);
        world.step(Map.of(0, Action.STAY, 1, Action.STAY));

        World restored = World.restore(config, world.exportState());

        assertThat(restored.views()).isEqualTo(world.views());
        assertThat(restored.views().getLast().died()).isTrue();
        assertThat(restored.views().getLast().neighbours()).containsExactly(1);
    }

    @Test
    void randomStateComesBackExactly() {
        Random random = new Random(7);
        random.nextGaussian();

        Random restored = RandomState.restore(RandomState.save(random));

        assertThat(restored.nextLong()).isEqualTo(random.nextLong());
        assertThat(restored.nextGaussian()).isEqualTo(random.nextGaussian());
    }

    @Test
    void onlyARandomMayComeBackFromASavedState() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(new java.util.ArrayList<>(List.of(1, 2)));
        }
        String notARandom = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());

        assertThatThrownBy(() -> RandomState.restore(notARandom)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aStateFromAnotherBoardSizeIsRefused() {
        WorldState state = new World(stageThree()).exportState();
        WorldConfig smaller = TestConfigs.stage1().size(10).build();

        assertThatThrownBy(() -> World.restore(smaller, state)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A generated stage 3 world with a store full of food and four shelters added on empty cells,
     * put in through a saved state, so births happen even with random moves.
     */
    private static World settlement(WorldConfig config) {
        WorldState state = new World(config).exportState();
        List<CellType> cells = new java.util.ArrayList<>(state.cells());
        List<Integer> amounts = new java.util.ArrayList<>(state.amounts());
        int placed = 0;
        for (int index = 0; index < cells.size() && placed < 5; index++) {
            if (cells.get(index) == CellType.EMPTY) {
                cells.set(index, placed == 0 ? CellType.STORE : CellType.SHELTER);
                amounts.set(index, placed == 0 ? 200 : 0);
                placed++;
            }
        }
        WorldState prepared = new WorldState(state.tick(), state.nextNpcId(), state.lastBirthTick(), cells,
                state.timers(), state.growsInto(), amounts, state.work(), state.npcs(), state.departed(),
                state.departedNeighbours(), state.retired(), state.retiringNext(), state.random());
        return World.restore(config, prepared);
    }

    /** Stage 3 rules on a 20x20 board, short seasons and lives, so 700 ticks show everything. */
    private static WorldConfig stageThree() {
        return TestConfigs.stage1().npcs(8).seasons(40).lifeLimit(0).energyRegen(1).lifespan(500, 0.2)
                .economy(new EconomyConfig(0.08, 20, 4, 15, 4, 2, 2, 4, 2, 1, 20))
                .population(new PopulationConfig(true, false, 3, 12, 2, 1, 20)).winterSurvived(5).build();
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

    private static final class Counter implements WorldListener {
        int seasons;
        int deaths;
        int births;

        @Override
        public void onLifeEnded(LifeEnded event) {
            deaths++;
        }

        @Override
        public void onNpcBorn(NpcBorn event) {
            births++;
        }

        @Override
        public void onSeasonChanged(SeasonChanged event) {
            seasons++;
        }

    }
}
