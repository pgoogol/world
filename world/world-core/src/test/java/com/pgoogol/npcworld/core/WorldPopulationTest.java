package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Step 3.6: founders in one camp, final death, old age without replacement and births from the stores. */
class WorldPopulationTest {

    /** Final death and old age without replacement; births: cost 5, reserve 2 per NPC, 10 ticks apart, at most 6. */
    private static final PopulationConfig STAGE_3 = new PopulationConfig(true, false, 0, 6, 5, 2, 10);

    @Test
    void foundersStartWithinTheRadiusOfOneCamp() {
        WorldConfig config = TestConfigs.stage1().size(40).npcs(20)
                .population(new PopulationConfig(true, false, 3, 50, 0, 0, 0)).build();

        List<Position> positions = new World(config).snapshot().npcs().stream().map(NpcSnapshot::position).toList();

        for (Position first : positions) {
            for (Position second : positions) {
                assertThat(Math.abs(first.x() - second.x())).isLessThanOrEqualTo(6);
                assertThat(Math.abs(first.y() - second.y())).isLessThanOrEqualTo(6);
            }
        }
    }

    @Test
    void aCollapseIsFinalWhenDeathIsFinal() {
        World world = onLava(STAGE_3);

        stay(world, 1);

        assertThat(world.population()).isEqualTo(1);
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::id).containsExactly(1);
    }

    @Test
    void theDeadShowUpOnceMoreSoTheirBrainGetsThePenalty() {
        World world = onLava(STAGE_3);
        stay(world, 1);

        List<NpcView> views = world.views();

        assertThat(views).hasSize(2);
        NpcView last = views.get(1);
        assertThat(last.npcId()).isZero();
        assertThat(last.died()).isTrue();
        assertThat(last.reward()).isEqualTo(-10.0);

        stay(world, 1);
        assertThat(world.views()).extracting(NpcView::npcId).containsExactly(1);
    }

    @Test
    void theBrainHearsTheDeadAreGoneOneTickAfterTheirLastView() {
        World world = onLava(STAGE_3);
        List<String> calls = new ArrayList<>();
        Brain brain = new Brain() {
            @Override
            public Map<Integer, Action> decide(long tick, List<NpcView> views) {
                calls.add("decide " + views.stream().map(view -> view.npcId() + (view.died() ? "+" : "")).toList());
                return SimulationTest.allDo(views, Action.STAY);
            }

            @Override
            public void npcsRetired(List<Integer> npcIds) {
                calls.add("retired " + npcIds);
            }
        };

        new Simulation(world, brain).run(4);

        assertThat(calls).containsExactly(
                "decide [0, 1]", "decide [1, 0+]", "retired [0]", "decide [1]", "decide [1]");
    }

    @Test
    void withoutFinalDeathACollapseStillRespawns() {
        World world = onLava(PopulationConfig.CLASSIC);

        stay(world, 1);

        assertThat(world.population()).isEqualTo(2);
    }

    @Test
    void oldAgeRemovesTheNpcWithoutAReplacement() {
        WorldConfig config = TestConfigs.emptyBoard().npcs(2).lifespan(100, 0).lifeLimit(0).population(STAGE_3).build();
        World world = new World(config);
        List<NpcRetired> retired = new ArrayList<>();
        world.addListener(new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
            }

            @Override
            public void onNpcRetired(NpcRetired event) {
                retired.add(event);
            }
        });

        stay(world, 50);

        assertThat(world.population()).isEqualTo(1);
        assertThat(retired).containsExactly(new NpcRetired(1, 50, 100, 0));
        assertThat(world.drainRetired()).containsExactly(1);
    }

    @Test
    void aSettlementCanDieOut() {
        WorldConfig config = TestConfigs.emptyBoard().npcs(2).lifespan(10, 0).lifeLimit(0).population(STAGE_3).build();
        World world = new World(config);

        stay(world, 10);

        assertThat(world.population()).isZero();
        assertThat(world.views()).isEmpty();
        world.step(Map.of());
        assertThat(world.tick()).isEqualTo(11);
    }

    @Test
    void aNewbornAppearsNextToTheFullestStoreAndCostsItsFood() {
        World world = settlement(store -> 30, 2);
        List<NpcBorn> born = listenForBirths(world);

        stay(world, 1);

        assertThat(world.population()).isEqualTo(3);
        NpcSnapshot newborn = world.snapshot().npcs().get(2);
        assertThat(newborn.id()).isEqualTo(2);
        assertThat(newborn.generation()).isEqualTo(1);
        assertThat(Math.abs(newborn.position().x() - 3)).isLessThanOrEqualTo(1);
        assertThat(Math.abs(newborn.position().y() - 3)).isLessThanOrEqualTo(1);
        assertThat(world.snapshot().amountAt(3, 3)).isEqualTo(25);
        assertThat(born).hasSize(1);
        assertThat(born.getFirst().parentId()).isPresent();
    }

    @Test
    void birthsWaitForTheInterval() {
        World world = settlement(store -> 100, 3);

        stay(world, 1);
        stay(world, 9);
        assertThat(world.population()).isEqualTo(3);
        stay(world, 1);

        assertThat(world.population()).isEqualTo(4);
    }

    @Test
    void noBirthWithoutTheCostOnTopOfTheReserve() {
        // Two NPCs need 5 + 2 * 2 = 9 portions in the stores.
        World poor = settlement(store -> 8, 2);
        stay(poor, 1);
        assertThat(poor.population()).isEqualTo(2);

        World rich = settlement(store -> 9, 2);
        stay(rich, 1);
        assertThat(rich.population()).isEqualTo(3);
    }

    @Test
    void noBirthWithoutAFreePlaceInTheShelters() {
        World world = settlement(store -> 100, 1);

        stay(world, 1);

        // One shelter for two NPCs: full.
        assertThat(world.population()).isEqualTo(2);
    }

    @Test
    void noBirthAboveTheLimit() {
        World world = settlement(store -> 1000, 10);

        stay(world, 200);

        assertThat(world.population()).isEqualTo(6);
    }

    @Test
    void sameSeedGivesTheSameSettlement() {
        assertThat(run(42)).isEqualTo(run(42));
    }

    private static WorldSnapshot run(long seed) {
        WorldConfig config = TestConfigs.stage1().seed(seed).npcs(5).seasons(20).lifeLimit(0).energyRegen(1)
                .lifespan(300, 0.2).economy(new EconomyConfig(0.05, 10, 4, 10, 4, 3, 2, 2, 2, 1, 10))
                .population(new PopulationConfig(true, false, 2, 10, 2, 1, 5)).build();
        World world = new World(config);
        java.util.Random choices = new java.util.Random(seed);
        Action[] palette = Action.values();
        Brain brain = (tick, views) -> {
            Map<Integer, Action> actions = new HashMap<>();
            views.forEach(view -> actions.put(view.npcId(), palette[choices.nextInt(palette.length)]));
            return actions;
        };
        new Simulation(world, brain).run(400);
        return world.snapshot();
    }

    /**
     * Two NPCs on a 7x7 board with a store at (3, 3) holding {@code food} portions and {@code shelters}
     * shelters (capacity 2 each) along the top row.
     */
    private static World settlement(java.util.function.IntUnaryOperator food, int shelters) {
        WorldConfig config = TestConfigs.emptyBoard().size(7).npcs(2).lifeLimit(0).energyRegen(1)
                .economy(TestConfigs.economy()).population(STAGE_3).build();
        Board board = new Board(7, 7);
        board.replace(new Position(3, 3), CellType.STORE);
        board.setAmount(new Position(3, 3), food.applyAsInt(0));
        for (int i = 0; i < shelters; i++) {
            board.replace(new Position(1 + i % 5, 1 + i / 5), CellType.SHELTER);
        }
        return new World(config, board);
    }

    private static List<NpcBorn> listenForBirths(World world) {
        List<NpcBorn> born = new ArrayList<>();
        world.addListener(new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
            }

            @Override
            public void onNpcBorn(NpcBorn event) {
                born.add(event);
            }
        });
        return born;
    }

    /** Two NPCs on a 5x5 board; NPC 0 stands on lava with 25 energy left. */
    private static World onLava(PopulationConfig population) {
        WorldConfig config = TestConfigs.emptyBoard().size(5).npcs(2).lifeLimit(0).population(population).build();
        Board board = new Board(5, 5);
        board.set(2, 2, CellType.LAVA);
        World world = new World(config, board);
        world.npc(0).position = new Position(2, 2);
        world.npc(0).energy = 25;
        world.npc(1).position = new Position(1, 1);
        return world;
    }

    private static void stay(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Map<Integer, Action> actions = new HashMap<>();
            world.snapshot().npcs().forEach(npc -> actions.put(npc.id(), Action.STAY));
            world.step(actions);
        }
    }
}
