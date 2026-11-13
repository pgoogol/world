package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Step 3.4: sowing a carried portion, ripening and harvesting a field. */
class WorldFieldsTest {

    private static final Position CENTRE = new Position(2, 2);

    @Test
    void plantingSowsOneCarriedPortionOnAnEmptyCell() {
        World world = world(board -> { });
        world.npc(0).food = 2;

        step(world, Action.PLANT);

        assertThat(world.npc(0).food).isEqualTo(1);
        assertThat(cell(world)).isEqualTo(CellType.FIELD_SOWN);
    }

    @Test
    void plantingNeedsCarriedFoodAndAnEmptyCell() {
        World empty = world(board -> { });
        step(empty, Action.PLANT);
        assertThat(cell(empty)).isEqualTo(CellType.EMPTY);

        for (CellType taken : List.of(CellType.TREE, CellType.WATER, CellType.FOOD, CellType.STUMP)) {
            World world = world(board -> board.set(CENTRE, taken));
            world.npc(0).food = 1;

            step(world, Action.PLANT);

            assertThat(cell(world)).isEqualTo(taken);
            assertThat(world.npc(0).food).isEqualTo(1);
        }
    }

    @Test
    void aSownFieldRipensWithTheConfiguredYield() {
        World world = world(board -> { });
        world.npc(0).food = 1;

        step(world, Action.PLANT);
        stay(world, 8);
        assertThat(cell(world)).isEqualTo(CellType.FIELD_SOWN);
        stay(world, 1);

        // The test economy ripens fields after 10 ticks of full growth (the sowing tick counts), with 4 portions.
        assertThat(cell(world)).isEqualTo(CellType.FIELD_RIPE);
        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(4);
        assertThat(world.views().getFirst().observation().hereAmount()).isEqualTo(4);
    }

    @Test
    void harvestingTakesOnePortionAtATimeUntilTheFieldIsEmpty() {
        World world = world(board -> {
            board.replace(CENTRE, CellType.FIELD_RIPE);
            board.setAmount(CENTRE, 4);
        });

        for (int portion = 1; portion <= 3; portion++) {
            step(world, Action.GATHER);
            assertThat(world.npc(0).food).isEqualTo(portion);
            assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(4 - portion);
            assertThat(cell(world)).isEqualTo(CellType.FIELD_RIPE);
        }
        step(world, Action.GATHER);

        assertThat(world.npc(0).food).isEqualTo(4);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);
        assertThat(world.snapshot().amountAt(2, 2)).isZero();
    }

    @Test
    void consumeEatsOnePortionStraightFromARipeField() {
        World world = world(board -> {
            board.replace(CENTRE, CellType.FIELD_RIPE);
            board.setAmount(CENTRE, 1);
        });
        world.npc(0).hunger = 60;
        world.npc(0).wood = 4;

        step(world, Action.CONSUME);

        assertThat(world.npc(0).hunger).isEqualTo(21);
        assertThat(world.npc(0).food).isZero();
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);
    }

    @Test
    void harvestStopsWhenTheInventoryIsFull() {
        World world = world(board -> {
            board.replace(CENTRE, CellType.FIELD_RIPE);
            board.setAmount(CENTRE, 4);
        });
        world.npc(0).wood = 3;

        step(world, Action.GATHER);
        step(world, Action.GATHER);

        assertThat(world.npc(0).food).isEqualTo(1);
        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(3);
    }

    @Test
    void oneSownPortionGivesFour() {
        World world = world(board -> { });
        world.npc(0).food = 1;

        step(world, Action.PLANT);
        stay(world, 10);
        for (int i = 0; i < 4; i++) {
            step(world, Action.GATHER);
        }

        assertThat(world.npc(0).food).isEqualTo(4);
    }

    @Test
    void aFieldSownBeforeWinterWaitsAndRipensInSpring() {
        // Seasons of 20 ticks; growth 100% / 100% / 50% / 0%. The field needs 10 ticks of full growth.
        World world = world(TestConfigs.emptyBoard().seasons(20), board -> { });
        world.npc(0).food = 1;
        world.npc(0).position = new Position(1, 1);
        stay(world, 55);
        world.npc(0).position = CENTRE;

        step(world, Action.PLANT);                 // tick 55, autumn: 5 ticks at half growth = 2.5 ticks
        world.npc(0).position = new Position(1, 1);
        stay(world, 4 + 20);                        // the rest of autumn and the whole winter
        assertThat(cell(world)).isEqualTo(CellType.FIELD_SOWN);
        stay(world, 7);                             // 7.5 ticks of spring are needed
        assertThat(cell(world)).isEqualTo(CellType.FIELD_SOWN);
        stay(world, 1);

        assertThat(world.tick()).isEqualTo(88);
        assertThat(cell(world)).isEqualTo(CellType.FIELD_RIPE);
    }

    @Test
    void sowingWhereWildFoodWasEatenCancelsItsRegrowth() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).hunger = 60;

        step(world, Action.GATHER);
        step(world, Action.PLANT);
        stay(world, 6);

        // Wild food would be back after 5 ticks; the field is still growing instead.
        assertThat(cell(world)).isEqualTo(CellType.FIELD_SOWN);
    }

    @Test
    void listenersHearSowingAndTheEndOfAHarvest() {
        World world = world(board -> { });
        List<Object> events = new ArrayList<>();
        world.addListener(new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
            }

            @Override
            public void onFieldSown(FieldSown event) {
                events.add(event);
            }

            @Override
            public void onFieldHarvested(FieldHarvested event) {
                events.add(event);
            }
        });
        world.npc(0).food = 1;

        step(world, Action.PLANT);
        stay(world, 10);
        for (int i = 0; i < 4; i++) {
            step(world, Action.GATHER);
        }

        assertThat(events).containsExactly(
                new FieldSown(1, 0, CENTRE),
                new FieldHarvested(15, 0, CENTRE));
    }

    private static World world(Consumer<Board> setup) {
        return world(TestConfigs.emptyBoard(), setup);
    }

    /** One NPC in the centre of a 5x5 board with the test economy. */
    private static World world(TestConfigs configs, Consumer<Board> setup) {
        WorldConfig config = configs.size(5).npcs(1).foodRegrowTicks(5).lifeLimit(0).energyRegen(1)
                .economy(TestConfigs.economy()).build();
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
            step(world, Action.STAY);
        }
    }
}
