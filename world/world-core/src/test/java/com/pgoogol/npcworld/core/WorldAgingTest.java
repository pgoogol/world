package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Stage 2 rules: ageing, death of old age, newborns, neighbours and positions. */
class WorldAgingTest {

    @Test
    void nobodyDiesOfOldAgeWhenAgeingIsOff() {
        World world = new World(TestConfigs.emptyBoard().npcs(2).build());
        List<NpcRetired> retired = new ArrayList<>();
        world.addListener(listener(retired, new ArrayList<>()));

        stay(world, 500);

        assertThat(retired).isEmpty();
        assertThat(world.drainRetired()).isEmpty();
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::id).containsExactly(0, 1);
    }

    @Test
    void foundersStartAtEvenlySpreadAges() {
        World world = new World(TestConfigs.emptyBoard().npcs(4).lifespan(1000, 0).build());

        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::totalAge).containsExactly(0L, 250L, 500L, 750L);
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::lifespan).containsOnly(1000L);
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::generation).containsOnly(0);
    }

    @Test
    void collapseDoesNotResetTheAgeSinceBirth() {
        Board board = new Board(5, 5);
        board.set(2, 2, CellType.LAVA);
        World world = new World(TestConfigs.emptyBoard().size(5).npcs(1).lifespan(1000, 0).build(), board);
        world.npc(0).position = new Position(2, 2);
        world.npc(0).energy = 25;

        stay(world, 1);

        assertThat(world.views().getFirst().died()).isTrue();
        assertThat(world.npc(0).age).isZero();
        assertThat(world.npc(0).totalAge).isEqualTo(1);
    }

    @Test
    void npcDiesOfOldAgeAtItsLifespanAndANewbornTakesItsPlace() {
        World world = new World(TestConfigs.emptyBoard().npcs(2).lifespan(100, 0).build());
        List<NpcRetired> retired = new ArrayList<>();
        List<NpcBorn> born = new ArrayList<>();
        world.addListener(listener(retired, born));

        stay(world, 49);
        assertThat(retired).isEmpty();
        stay(world, 1);

        // Slot 1 started at age 50, so it reaches 100 after 50 ticks.
        assertThat(retired).containsExactly(new NpcRetired(1, 50, 100, 0));
        assertThat(born).containsExactly(new NpcBorn(2, 50, 1, Optional.of(0)));
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::id).containsExactly(0, 2);
    }

    @Test
    void newbornStartsFreshNextToALivingNpc() {
        World world = new World(TestConfigs.emptyBoard().npcs(2).lifespan(100, 0).build());

        stay(world, 50);

        NpcSnapshot parent = world.snapshot().npcs().get(0);
        NpcSnapshot newborn = world.snapshot().npcs().get(1);
        assertThat(newborn.totalAge()).isZero();
        assertThat(newborn.age()).isZero();
        assertThat(newborn.generation()).isEqualTo(1);
        assertThat(newborn.energy()).isEqualTo(100);
        assertThat(newborn.hunger()).isZero();
        assertThat(Math.abs(newborn.position().x() - parent.position().x())).isLessThanOrEqualTo(1);
        assertThat(Math.abs(newborn.position().y() - parent.position().y())).isLessThanOrEqualTo(1);

        NpcView view = world.views().get(1);
        assertThat(view.npcId()).isEqualTo(2);
        assertThat(view.reward()).isZero();
        assertThat(view.died()).isFalse();
        assertThat(view.truncated()).isFalse();
        assertThat(view.neighbours()).containsExactly(0);
    }

    @Test
    void retiredIdsAreReportedOnce() {
        World world = new World(TestConfigs.emptyBoard().npcs(2).lifespan(100, 0).build());

        stay(world, 50);

        assertThat(world.drainRetired()).containsExactly(1);
        assertThat(world.drainRetired()).isEmpty();
    }

    @Test
    void simulationTellsTheBrainWhoRetiredBeforeTheNextDecision() {
        World world = new World(TestConfigs.emptyBoard().npcs(2).lifespan(100, 0).build());
        List<String> calls = new ArrayList<>();
        Brain brain = new Brain() {
            @Override
            public Map<Integer, Action> decide(long tick, List<NpcView> views) {
                if (tick == 50) {
                    calls.add("decide " + views.stream().map(NpcView::npcId).toList());
                }
                return SimulationTest.allDo(views, Action.STAY);
            }

            @Override
            public void npcsRetired(List<Integer> npcIds) {
                calls.add("retired " + npcIds);
            }
        };

        new Simulation(world, brain).run(51);

        assertThat(calls).containsExactly("retired [1]", "decide [0, 2]");
    }

    @Test
    void nobodyAgesDuringTheWarmUp() {
        World world = new World(TestConfigs.emptyBoard().npcs(2).lifespan(100, 0).agingStartTick(30).build());

        stay(world, 30);
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::totalAge).containsExactly(0L, 50L);

        stay(world, 10);
        assertThat(world.snapshot().npcs()).extracting(NpcSnapshot::totalAge).containsExactly(10L, 60L);
    }

    @Test
    void lifespansVaryWithinTheConfiguredSpread() {
        World world = new World(TestConfigs.emptyBoard().npcs(5).lifespan(1000, 0.2).build());

        List<Long> lifespans = world.snapshot().npcs().stream().map(NpcSnapshot::lifespan).toList();

        assertThat(lifespans).allMatch(lifespan -> lifespan >= 800 && lifespan <= 1200);
        assertThat(lifespans).doesNotHaveDuplicates();
    }

    @Test
    void generationGrowsWithEveryReplacementInASlot() {
        World world = new World(TestConfigs.emptyBoard().npcs(1).lifespan(100, 0).build());

        stay(world, 350);

        NpcSnapshot npc = world.snapshot().npcs().getFirst();
        assertThat(npc.generation()).isEqualTo(3);
        assertThat(npc.id()).isEqualTo(3);
        assertThat(npc.totalAge()).isEqualTo(50);
    }

    @Test
    void viewListsTheNpcsStandingInTheSurroundingCells() {
        World world = new World(TestConfigs.emptyBoard().size(8).npcs(3).build(), new Board(8, 8));
        world.npc(0).position = new Position(2, 2);
        world.npc(1).position = new Position(3, 3);
        world.npc(2).position = new Position(5, 5);

        List<NpcView> views = world.views();

        assertThat(views.get(0).neighbours()).containsExactly(1);
        assertThat(views.get(1).neighbours()).containsExactly(0);
        assertThat(views.get(2).neighbours()).isEmpty();

        world.npc(2).position = new Position(3, 3);
        assertThat(world.views().get(1).neighbours()).containsExactly(0, 2);
    }

    @Test
    void observationCarriesTheNpcPosition() {
        World world = new World(TestConfigs.emptyBoard().size(8).npcs(1).build(), new Board(8, 8));
        world.npc(0).position = new Position(4, 6);

        assertThat(world.views().getFirst().observation().position()).isEqualTo(new Position(4, 6));
    }

    @Test
    void sameSeedGivesTheSameThreeGenerations() {
        assertThat(generations(42)).isEqualTo(generations(42));
        assertThat(generations(42).snapshot()).isNotEqualTo(generations(43).snapshot());
        assertThat(generations(42).snapshot().npcs()).allMatch(npc -> npc.generation() >= 3);
    }

    private record Run(WorldSnapshot snapshot, List<NpcBorn> born, List<LifeEnded> lives) {
    }

    private static Run generations(long seed) {
        World world = new World(TestConfigs.stage1().seed(seed).npcs(6).lifespan(300, 0.2).build());
        List<NpcBorn> born = new ArrayList<>();
        List<LifeEnded> lives = new ArrayList<>();
        world.addListener(new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
                lives.add(event);
            }

            @Override
            public void onNpcBorn(NpcBorn event) {
                born.add(event);
            }
        });
        Random choices = new Random(seed);
        Brain brain = (tick, views) -> {
            Map<Integer, Action> actions = new HashMap<>();
            for (NpcView view : views) {
                actions.put(view.npcId(), Action.values()[choices.nextInt(Action.values().length)]);
            }
            return actions;
        };

        new Simulation(world, brain).run(1500);

        return new Run(world.snapshot(), born, lives);
    }

    private static WorldListener listener(List<NpcRetired> retired, List<NpcBorn> born) {
        return new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
            }

            @Override
            public void onNpcRetired(NpcRetired event) {
                retired.add(event);
            }

            @Override
            public void onNpcBorn(NpcBorn event) {
                born.add(event);
            }
        };
    }

    private static void stay(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Map<Integer, Action> actions = new HashMap<>();
            world.snapshot().npcs().forEach(npc -> actions.put(npc.id(), Action.STAY));
            world.step(actions);
        }
    }
}
