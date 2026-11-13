package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Step 3.2: the year, growth that depends on the season, winter rot and cold. */
class WorldSeasonsTest {

    private static final Position CENTRE = new Position(2, 2);
    private static final int SEASON = 20;

    @Test
    void seasonsFollowEachOtherAndTheYearRepeats() {
        SeasonConfig seasons = new SeasonConfig(SEASON, List.of(100, 100, 50, 0), 1, true);

        assertThat(seasons.seasonAt(0)).isEqualTo(Season.SPRING);
        assertThat(seasons.seasonAt(19)).isEqualTo(Season.SPRING);
        assertThat(seasons.seasonAt(20)).isEqualTo(Season.SUMMER);
        assertThat(seasons.seasonAt(40)).isEqualTo(Season.AUTUMN);
        assertThat(seasons.seasonAt(60)).isEqualTo(Season.WINTER);
        assertThat(seasons.seasonAt(80)).isEqualTo(Season.SPRING);
        assertThat(seasons.ticksLeftAt(0)).isEqualTo(20);
        assertThat(seasons.ticksLeftAt(19)).isEqualTo(1);
        assertThat(seasons.growthAt(45)).isEqualTo(50);
        assertThat(seasons.growthAt(65)).isZero();
    }

    @Test
    void withoutSeasonsGrowthIsAlwaysFull() {
        assertThat(SeasonConfig.NONE.seasonAt(12345)).isNull();
        assertThat(SeasonConfig.NONE.growthAt(12345)).isEqualTo(100);
        assertThat(SeasonConfig.NONE.ticksLeftAt(12345)).isZero();
    }

    @Test
    void observationTellsTheSeasonAndTheTicksLeftInIt() {
        World world = world(board -> board.set(CENTRE, CellType.EMPTY));

        stay(world, 25);

        Observation observation = world.views().getFirst().observation();
        assertThat(observation.season()).isEqualTo(2);
        assertThat(observation.seasonTicksLeft()).isEqualTo(15);
        assertThat(world.snapshot().season()).isEqualTo(Season.SUMMER);
    }

    @Test
    void eatenFoodGrowsBackAtFullSpeedInSpring() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).hunger = 60;

        step(world, Action.CONSUME);
        stay(world, 3);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);
        stay(world, 1);

        // Regrowth takes 5 ticks of full growth.
        assertThat(world.tick()).isEqualTo(5);
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
    }

    @Test
    void autumnGrowsAtHalfSpeed() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        stay(world, 2 * SEASON);

        step(world, Action.CONSUME);
        stay(world, 8);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);
        stay(world, 1);

        assertThat(world.tick()).isEqualTo(2 * SEASON + 10);
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
    }

    @Test
    void nothingGrowsInWinter() {
        SeasonConfig noRot = new SeasonConfig(SEASON, List.of(100, 100, 50, 0), 1, false);
        World world = world(TestConfigs.emptyBoard().seasons(noRot), board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).position = new Position(1, 1);
        stay(world, 3 * SEASON);
        world.npc(0).position = CENTRE;

        step(world, Action.CONSUME);
        world.npc(0).position = new Position(1, 1);
        stay(world, SEASON - 1);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);

        // Spring: five ticks of full growth.
        stay(world, 5);
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
    }

    @Test
    void wildFoodRotsOnTheFirstDayOfWinterAndGrowsBackInSpring() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).position = new Position(1, 1);

        stay(world, 3 * SEASON - 1);
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
        stay(world, 1);
        assertThat(world.snapshot().season()).isEqualTo(Season.WINTER);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);

        stay(world, SEASON + 4);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);
        stay(world, 1);
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
    }

    @Test
    void ripeFieldsRotInWinterButSownFieldsWait() {
        World world = world(board -> {
            board.replace(new Position(1, 1), CellType.FIELD_RIPE);
            board.setAmount(new Position(1, 1), 3);
            board.startGrowth(new Position(3, 3), CellType.FIELD_SOWN, CellType.FIELD_RIPE, 1000);
        });

        stay(world, 3 * SEASON);

        assertThat(world.snapshot().cellAt(1, 1)).isEqualTo(CellType.EMPTY);
        assertThat(world.snapshot().amountAt(1, 1)).isZero();
        assertThat(world.snapshot().cellAt(3, 3)).isEqualTo(CellType.FIELD_SOWN);
    }

    @Test
    void coldDrainsEnergyOutsideAShelterInWinter() {
        World world = world(board -> { });
        world.npc(0).energy = 50;
        stay(world, 3 * SEASON);
        int atWinter = world.npc(0).energy;

        stay(world, 10);

        assertThat(world.npc(0).energy).isEqualTo(atWinter - 10);
    }

    @Test
    void shelterKeepsTheColdOutForItsCapacity() {
        WorldConfig config = TestConfigs.emptyBoard().size(5).npcs(3).seasons(SEASON).energyRegen(0)
                .economy(TestConfigs.economy()).build();
        Board board = new Board(5, 5);
        board.replace(CENTRE, CellType.SHELTER);
        World world = new World(config, board);
        for (int slot = 0; slot < 3; slot++) {
            world.npc(slot).position = CENTRE;
        }
        stay(world, 3 * SEASON);

        stay(world, 10);

        // Shelter capacity is 2: the first two NPCs by slot are protected, the third is not.
        assertThat(world.npc(0).energy).isEqualTo(100);
        assertThat(world.npc(1).energy).isEqualTo(100);
        assertThat(world.npc(2).energy).isLessThan(100);
    }

    @Test
    void energyComesBackOnTicksWithoutAnyLoss() {
        World world = world(TestConfigs.emptyBoard().seasons(SEASON).energyRegen(2), board -> { });
        world.npc(0).energy = 50;

        stay(world, 5);

        assertThat(world.npc(0).energy).isEqualTo(60);
    }

    @Test
    void everyNpcAliveOnTheFirstDayOfSpringGetsTheWinterBonus() {
        World world = world(TestConfigs.emptyBoard().seasons(SEASON).energyRegen(1).winterSurvived(5), board -> { });
        world.npc(0).position = CENTRE;

        stay(world, 4 * SEASON - 1);
        assertThat(world.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));
        stay(world, 1);

        assertThat(world.snapshot().season()).isEqualTo(Season.SPRING);
        assertThat(world.views().getFirst().reward()).isCloseTo(5.1, within(1e-9));
        stay(world, 1);
        assertThat(world.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void listenersHearEverySeasonChange() {
        World world = world(board -> { });
        List<SeasonChanged> changes = new ArrayList<>();
        world.addListener(new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
            }

            @Override
            public void onSeasonChanged(SeasonChanged event) {
                changes.add(event);
            }
        });

        stay(world, 4 * SEASON);

        assertThat(changes).containsExactly(
                new SeasonChanged(20, Season.SUMMER, 0),
                new SeasonChanged(40, Season.AUTUMN, 0),
                new SeasonChanged(60, Season.WINTER, 0),
                new SeasonChanged(80, Season.SPRING, 1));
    }

    @Test
    void sameSeedGivesTheSameYear() {
        assertThat(year(42)).isEqualTo(year(42));
        assertThat(year(42)).isNotEqualTo(year(43));
    }

    private static WorldSnapshot year(long seed) {
        WorldConfig config = TestConfigs.stage1().seed(seed).seasons(SEASON).energyRegen(1).lifeLimit(0).build();
        World world = new World(config);
        Random choices = new Random(seed);
        Brain brain = (tick, views) -> {
            Map<Integer, Action> actions = new HashMap<>();
            for (NpcView view : views) {
                actions.put(view.npcId(), Action.values()[choices.nextInt(6)]);
            }
            return actions;
        };
        new Simulation(world, brain).run(4 * SEASON * 3);
        return world.snapshot();
    }

    private static World world(java.util.function.Consumer<Board> setup) {
        return world(TestConfigs.emptyBoard().seasons(SEASON).energyRegen(1), setup);
    }

    /** One NPC in the centre of a 5x5 board, seasons of 20 ticks, food regrows after 5 ticks. */
    private static World world(TestConfigs configs, java.util.function.Consumer<Board> setup) {
        WorldConfig config = configs.size(5).npcs(1).foodRegrowTicks(5).lifeLimit(0).build();
        Board board = new Board(5, 5);
        setup.accept(board);
        World world = new World(config, board);
        world.npc(0).position = CENTRE;
        return world;
    }

    private static CellType cell(World world) {
        return world.snapshot().cellAt(CENTRE.x(), CENTRE.y());
    }

    private static void step(World world, Action action) {
        world.step(Map.of(world.npc(0).id, action));
    }

    private static void stay(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Map<Integer, Action> actions = new HashMap<>();
            world.snapshot().npcs().forEach(npc -> actions.put(npc.id(), Action.STAY));
            world.step(actions);
        }
    }
}
