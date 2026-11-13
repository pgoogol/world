package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Step 3.3: carrying food and wood, felling trees and eating what you carry. */
class WorldItemsTest {

    private static final Position CENTRE = new Position(2, 2);

    @Test
    void gatheringWildFoodPutsItInTheInventory() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));

        step(world, Action.GATHER);

        assertThat(world.npc(0).food).isEqualTo(1);
        assertThat(cell(world)).isEqualTo(CellType.EMPTY);
        assertThat(world.views().getFirst().observation().carriedFood()).isEqualTo(1);
    }

    @Test
    void gatheredWildFoodGrowsBackLikeEatenFood() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));

        step(world, Action.GATHER);
        stay(world, 4);

        // Food regrows after 5 ticks of full growth in the test config.
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
    }

    @Test
    void fellingATreeGivesWoodAndLeavesAStumpThatGrowsBack() {
        World world = world(board -> board.set(CENTRE, CellType.TREE));

        step(world, Action.GATHER);
        assertThat(world.npc(0).wood).isEqualTo(1);
        assertThat(cell(world)).isEqualTo(CellType.STUMP);

        // Trees regrow after 10 ticks of full growth in the test economy.
        stay(world, 8);
        assertThat(cell(world)).isEqualTo(CellType.STUMP);
        stay(world, 1);
        assertThat(cell(world)).isEqualTo(CellType.TREE);
    }

    @Test
    void gatheringOnAStumpOrAnEmptyCellGivesNothing() {
        World world = world(board -> board.set(CENTRE, CellType.STUMP));

        step(world, Action.GATHER);
        world.npc(0).position = new Position(1, 1);
        step(world, Action.GATHER);

        assertThat(world.npc(0).food + world.npc(0).wood).isZero();
    }

    @Test
    void aFullInventoryTakesNothingMore() {
        World world = world(board -> board.set(CENTRE, CellType.TREE));
        world.npc(0).food = 3;
        world.npc(0).wood = 1;

        step(world, Action.GATHER);

        assertThat(world.npc(0).wood).isEqualTo(1);
        assertThat(cell(world)).isEqualTo(CellType.TREE);
    }

    @Test
    void eatingCarriedFoodLowersHungerAndPaysWhenHungry() {
        World world = world(board -> { });
        world.npc(0).food = 2;
        world.npc(0).hunger = 60;

        step(world, Action.EAT_CARRIED);

        assertThat(world.npc(0).food).isEqualTo(1);
        assertThat(world.npc(0).hunger).isEqualTo(60 - 40 + 1);
        assertThat(world.views().getFirst().reward()).isCloseTo(1.1, within(1e-9));
    }

    @Test
    void eatingWithoutCarriedFoodDoesNothing() {
        World world = world(board -> { });
        world.npc(0).hunger = 60;

        step(world, Action.EAT_CARRIED);

        assertThat(world.npc(0).hunger).isEqualTo(61);
        assertThat(world.views().getFirst().reward()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void consumeStillEatsWildFoodDirectly() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));
        world.npc(0).hunger = 60;

        step(world, Action.CONSUME);

        assertThat(world.npc(0).hunger).isEqualTo(21);
        assertThat(world.npc(0).food).isZero();
    }

    @Test
    void withoutAnEconomyNothingCanBeCarried() {
        WorldConfig config = TestConfigs.emptyBoard().size(5).npcs(1).lifeLimit(0).build();
        Board board = new Board(5, 5);
        board.set(CENTRE, CellType.FOOD);
        World world = new World(config, board);
        world.npc(0).position = CENTRE;

        step(world, Action.GATHER);

        assertThat(world.npc(0).food).isZero();
        assertThat(cell(world)).isEqualTo(CellType.FOOD);
    }

    @Test
    void inventoryNeverOverflowsNorGoesNegative() {
        WorldConfig config = TestConfigs.stage1().economy(new EconomyConfig(0.1, 10, 4, 10, 4, 3, 2, 2, 2, 1, 10))
                .lifeLimit(0).energyRegen(1).build();
        World world = new World(config);
        Random choices = new Random(7);
        Action[] palette = {Action.UP, Action.DOWN, Action.LEFT, Action.RIGHT, Action.GATHER, Action.GATHER,
                Action.EAT_CARRIED, Action.CONSUME};

        for (int tick = 0; tick < 3000; tick++) {
            Map<Integer, Action> actions = new HashMap<>();
            for (NpcView view : world.views()) {
                actions.put(view.npcId(), palette[choices.nextInt(palette.length)]);
            }
            world.step(actions);
            for (NpcSnapshot npc : world.snapshot().npcs()) {
                assertThat(npc.carriedFood()).isBetween(0, 4);
                assertThat(npc.carriedWood()).isBetween(0, 4);
                assertThat(npc.carriedFood() + npc.carriedWood()).isLessThanOrEqualTo(4);
            }
        }
    }

    @Test
    void everyGatherAddsExactlyOneItem() {
        World world = world(board -> {
            board.set(CENTRE, CellType.FOOD);
            board.set(new Position(3, 2), CellType.TREE);
        });

        step(world, Action.GATHER);
        step(world, Action.RIGHT);
        step(world, Action.GATHER);
        step(world, Action.GATHER);

        assertThat(world.npc(0).food).isEqualTo(1);
        assertThat(world.npc(0).wood).isEqualTo(1);
    }

    /** One NPC in the centre of a 5x5 board with the test economy and no seasons. */
    private static World world(Consumer<Board> setup) {
        WorldConfig config = TestConfigs.emptyBoard().size(5).npcs(1).foodRegrowTicks(5).lifeLimit(0)
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
