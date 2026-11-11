package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** One test per world rule. Boards are hand-made 5x5 (3x3 inside the wall) with a single NPC. */
class WorldRulesTest {

    private static final Position CENTRE = new Position(2, 2);

    @Test
    void moveChangesPositionByOneCell() {
        World world = world(board -> { });

        step(world, Action.UP);
        assertThat(world.npc(0).position).isEqualTo(new Position(2, 1));
        step(world, Action.RIGHT);
        assertThat(world.npc(0).position).isEqualTo(new Position(3, 1));
        step(world, Action.DOWN);
        assertThat(world.npc(0).position).isEqualTo(new Position(3, 2));
        step(world, Action.LEFT);
        assertThat(world.npc(0).position).isEqualTo(CENTRE);
        step(world, Action.STAY);
        assertThat(world.npc(0).position).isEqualTo(CENTRE);
    }

    @Test
    void moveIntoWallLeavesNpcInPlace() {
        World world = world(board -> { });
        world.npc(0).position = new Position(1, 1);

        step(world, Action.UP);
        step(world, Action.LEFT);

        assertThat(world.npc(0).position).isEqualTo(new Position(1, 1));
    }

    @Test
    void npcsCanShareACell() {
        WorldConfig config = TestConfigs.emptyBoard().size(5).npcs(2).build();
        World world = new World(config, new Board(5, 5));
        world.npc(0).position = new Position(1, 2);
        world.npc(1).position = new Position(3, 2);

        world.step(Map.of(0, Action.RIGHT, 1, Action.LEFT));

        assertThat(world.npc(0).position).isEqualTo(CENTRE);
        assertThat(world.npc(1).position).isEqualTo(CENTRE);
    }

    @Test
    void hungerAndThirstGrowEveryTickUpToTheMaximum() {
        World world = world(board -> { });

        step(world, Action.STAY);
        assertThat(world.npc(0).hunger).isEqualTo(1);
        assertThat(world.npc(0).thirst).isEqualTo(1);

        world.npc(0).hunger = 100;
        world.npc(0).thirst = 99;
        step(world, Action.STAY);
        assertThat(world.npc(0).hunger).isEqualTo(100);
        assertThat(world.npc(0).thirst).isEqualTo(100);
    }

    @Test
    void energyStaysWhenNeedsAreBelowTheThreshold() {
        World world = world(board -> { });
        world.npc(0).hunger = 78;
        world.npc(0).thirst = 78;

        step(world, Action.STAY);

        assertThat(world.npc(0).energy).isEqualTo(100);
    }

    @Test
    void energyDropsByOneForEachNeedAtTheThreshold() {
        World world = world(board -> { });
        world.npc(0).hunger = 79;

        step(world, Action.STAY);
        assertThat(world.npc(0).energy).isEqualTo(99);

        world.npc(0).thirst = 79;
        step(world, Action.STAY);
        assertThat(world.npc(0).energy).isEqualTo(97);
    }

    @Test
    void lavaDrainsEnergyEveryTick() {
        World world = world(board -> board.set(CENTRE, CellType.LAVA));
        world.npc(0).position = CENTRE;

        step(world, Action.STAY);
        assertThat(world.npc(0).energy).isEqualTo(75);
        step(world, Action.STAY);
        assertThat(world.npc(0).energy).isEqualTo(50);
    }

    @Test
    void energyRegenerationIsOffByDefaultAndFollowsTheConfig() {
        World off = world(board -> { });
        off.npc(0).energy = 50;
        step(off, Action.STAY);
        assertThat(off.npc(0).energy).isEqualTo(50);

        World on = world(TestConfigs.emptyBoard().energyRegen(2), board -> { });
        on.npc(0).energy = 50;
        step(on, Action.STAY);
        assertThat(on.npc(0).energy).isEqualTo(52);
    }

    @Test
    void eatingRemovesFoodAndLowersHunger() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).hunger = 60;

        step(world, Action.CONSUME);

        assertThat(world.npc(0).hunger).isEqualTo(60 - 40 + 1);
        assertThat(world.snapshot().cellAt(2, 2)).isEqualTo(CellType.EMPTY);
    }

    @Test
    void hungerNeverGoesBelowZero() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).hunger = 10;

        step(world, Action.CONSUME);

        assertThat(world.npc(0).hunger).isEqualTo(1);
    }

    @Test
    void drinkingLowersThirstAndWaterStays() {
        World world = world(board -> board.set(CENTRE, CellType.WATER));
        world.npc(0).thirst = 60;

        step(world, Action.CONSUME);
        step(world, Action.CONSUME);

        assertThat(world.npc(0).thirst).isEqualTo(1);
        assertThat(world.snapshot().cellAt(2, 2)).isEqualTo(CellType.WATER);
    }

    @Test
    void consumingOnAnEmptyCellDoesNothing() {
        World world = world(board -> { });
        world.npc(0).hunger = 60;

        step(world, Action.CONSUME);

        assertThat(world.npc(0).hunger).isEqualTo(61);
        assertThat(world.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void foodGrowsBackAfterTheConfiguredNumberOfTicks() {
        World world = world(TestConfigs.emptyBoard().foodRegrowTicks(5), board -> board.set(CENTRE, CellType.FOOD));

        step(world, Action.CONSUME);
        for (int i = 0; i < 4; i++) {
            assertThat(world.snapshot().cellAt(2, 2)).isEqualTo(CellType.EMPTY);
            step(world, Action.STAY);
        }

        assertThat(world.tick()).isEqualTo(5);
        assertThat(world.snapshot().cellAt(2, 2)).isEqualTo(CellType.FOOD);
    }

    @Test
    void everySurvivedTickPaysASmallReward() {
        World world = world(board -> { });

        step(world, Action.STAY);

        assertThat(world.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void eatingAndDrinkingPayOnlyWhenTheNeedIsHighEnough() {
        World fed = world(board -> board.set(CENTRE, CellType.FOOD));
        fed.npc(0).hunger = 49;
        step(fed, Action.CONSUME);
        assertThat(fed.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));

        World hungry = world(board -> board.set(CENTRE, CellType.FOOD));
        hungry.npc(0).hunger = 50;
        step(hungry, Action.CONSUME);
        assertThat(hungry.views().getFirst().reward()).isCloseTo(1.1, within(1e-9));

        World thirsty = world(board -> board.set(CENTRE, CellType.WATER));
        thirsty.npc(0).thirst = 50;
        step(thirsty, Action.CONSUME);
        assertThat(thirsty.views().getFirst().reward()).isCloseTo(1.1, within(1e-9));
    }

    @Test
    void npcDiesAtZeroEnergyAndRespawnsFresh() {
        World world = world(board -> board.set(CENTRE, CellType.LAVA));
        List<LifeEnded> events = new ArrayList<>();
        world.addListener(events::add);
        Npc npc = world.npc(0);
        npc.position = CENTRE;
        npc.energy = 25;
        npc.hunger = 30;
        npc.age = 7;

        step(world, Action.STAY);

        NpcView view = world.views().getFirst();
        assertThat(view.died()).isTrue();
        assertThat(view.truncated()).isFalse();
        assertThat(view.reward()).isEqualTo(-10.0);
        assertThat(view.finalObservation()).isEmpty();
        assertThat(view.observation().energy()).isEqualTo(100);
        assertThat(view.observation().hunger()).isZero();
        assertThat(view.observation().thirst()).isZero();
        assertThat(npc.position).isNotEqualTo(CENTRE);
        assertThat(npc.age).isZero();
        assertThat(events).containsExactly(new LifeEnded(0, 1, 8, LifeEnded.Cause.DEATH));
    }

    @Test
    void deathFlagLastsOneTick() {
        World world = world(board -> board.set(CENTRE, CellType.LAVA));
        world.npc(0).position = CENTRE;
        world.npc(0).energy = 25;
        step(world, Action.STAY);

        step(world, Action.STAY);

        assertThat(world.views().getFirst().died()).isFalse();
        assertThat(world.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void lifeLimitTruncatesTheEpisodeWithoutAPenalty() {
        World world = world(TestConfigs.emptyBoard().lifeLimit(3), board -> { });
        List<LifeEnded> events = new ArrayList<>();
        world.addListener(events::add);

        step(world, Action.STAY);
        step(world, Action.STAY);
        assertThat(world.views().getFirst().truncated()).isFalse();
        step(world, Action.STAY);

        NpcView view = world.views().getFirst();
        assertThat(view.truncated()).isTrue();
        assertThat(view.died()).isFalse();
        assertThat(view.reward()).isCloseTo(0.1, within(1e-9));
        assertThat(view.finalObservation()).isPresent();
        assertThat(view.finalObservation().get().hunger()).isEqualTo(3);
        assertThat(view.observation().hunger()).isZero();
        assertThat(world.npc(0).age).isZero();
        assertThat(events).containsExactly(new LifeEnded(0, 3, 3, LifeEnded.Cause.LIFE_LIMIT));
    }

    @Test
    void respawnNeverLandsOnLavaOrWall() {
        World world = world(board -> {
            for (int y = 1; y <= 3; y++) {
                for (int x = 1; x <= 3; x++) {
                    board.set(x, y, CellType.LAVA);
                }
            }
            board.set(3, 3, CellType.EMPTY);
        });

        for (int i = 0; i < 20; i++) {
            Npc npc = world.npc(0);
            assertThat(npc.position).isEqualTo(new Position(3, 3));
            npc.position = CENTRE;
            npc.energy = 1;
            step(world, Action.STAY);
        }
    }

    private static World world(Consumer<Board> setup) {
        return world(TestConfigs.emptyBoard(), setup);
    }

    /** One NPC in the centre of a 5x5 board prepared by {@code setup}. */
    private static World world(TestConfigs configs, Consumer<Board> setup) {
        WorldConfig config = configs.size(5).npcs(1).build();
        Board board = new Board(5, 5);
        setup.accept(board);
        World world = new World(config, board);
        if (board.isSafe(CENTRE.x(), CENTRE.y())) {
            world.npc(0).position = CENTRE;
        }
        return world;
    }

    private static void step(World world, Action action) {
        world.step(Map.of(0, action));
    }
}
