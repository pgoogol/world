package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Step 3.5: building sites, shelters and stores. The test economy: a shelter takes 3 wood and
 * 2 ticks of work and shelters 2 NPCs; a store takes 2 wood and 1 tick of work and holds 10 portions.
 */
class WorldBuildingTest {

    private static final Position CENTRE = new Position(2, 2);

    @Test
    void buildingOnAnEmptyCellStartsASite() {
        World world = world(board -> { });

        step(world, Action.BUILD_SHELTER);

        assertThat(cell(world)).isEqualTo(CellType.SITE_SHELTER);
        assertThat(world.snapshot().amountAt(2, 2)).isZero();
    }

    @Test
    void sitesNeedAnEmptyCell() {
        for (CellType taken : List.of(CellType.TREE, CellType.WATER, CellType.FOOD, CellType.FIELD_SOWN)) {
            World world = world(board -> board.set(CENTRE, taken));
            world.npc(0).wood = 2;

            step(world, Action.BUILD_STORE);

            assertThat(cell(world)).isEqualTo(taken);
            assertThat(world.npc(0).wood).isEqualTo(2);
        }
    }

    @Test
    void everyBuildActionBringsOneWoodUntilTheSiteHasEnough() {
        World world = world(board -> { });
        world.npc(0).wood = 4;

        step(world, Action.BUILD_SHELTER);
        step(world, Action.BUILD_SHELTER);
        step(world, Action.BUILD_SHELTER);
        assertThat(world.npc(0).wood).isEqualTo(2);
        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(2);
        assertThat(world.views().getFirst().observation().hereAmount()).isEqualTo(2);
        step(world, Action.BUILD_SHELTER);

        assertThat(world.npc(0).wood).isEqualTo(1);
        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(3);
        assertThat(cell(world)).isEqualTo(CellType.SITE_SHELTER);
    }

    @Test
    void aSiteWithoutEnoughWoodDoesNotProgressWithoutWood() {
        World world = world(board -> { });
        world.npc(0).wood = 1;

        step(world, Action.BUILD_SHELTER);
        step(world, Action.BUILD_SHELTER);
        step(world, Action.BUILD_SHELTER);
        step(world, Action.BUILD_SHELTER);

        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(1);
        assertThat(cell(world)).isEqualTo(CellType.SITE_SHELTER);
    }

    @Test
    void workAfterTheWoodFinishesTheBuilding() {
        World world = world(board -> { });
        world.npc(0).wood = 3;

        for (int i = 0; i < 1 + 3 + 1; i++) {
            step(world, Action.BUILD_SHELTER);
        }
        assertThat(cell(world)).isEqualTo(CellType.SITE_SHELTER);
        step(world, Action.BUILD_SHELTER);

        assertThat(cell(world)).isEqualTo(CellType.SHELTER);
        assertThat(world.snapshot().amountAt(2, 2)).isZero();
        assertThat(world.npc(0).wood).isZero();
    }

    @Test
    void severalNpcsBuildTogether() {
        WorldConfig config = config(TestConfigs.emptyBoard()).npcs(3).build();
        World world = new World(config, new Board(5, 5));
        for (int slot = 0; slot < 3; slot++) {
            world.npc(slot).position = CENTRE;
            world.npc(slot).wood = 1;
        }

        everyone(world, Action.BUILD_STORE);    // the first starts the site, the others bring wood
        assertThat(cell(world)).isEqualTo(CellType.SITE_STORE);
        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(2);
        everyone(world, Action.BUILD_STORE);    // one tick of work is enough for a store

        assertThat(cell(world)).isEqualTo(CellType.STORE);
        // The one who started the site kept its wood; the other two brought theirs.
        assertThat(world.npc(0).wood).isEqualTo(1);
        assertThat(world.npc(2).wood).isZero();
    }

    @Test
    void buildingTheOtherKindOnASiteDoesNothing() {
        World world = world(board -> { });
        world.npc(0).wood = 2;

        step(world, Action.BUILD_SHELTER);
        step(world, Action.BUILD_STORE);

        assertThat(cell(world)).isEqualTo(CellType.SITE_SHELTER);
        assertThat(world.npc(0).wood).isEqualTo(2);
    }

    @Test
    void depositPutsAllCarriedFoodIntoTheStoreUpToItsCapacity() {
        World world = world(board -> {
            board.replace(CENTRE, CellType.STORE);
            board.setAmount(CENTRE, 8);
        });
        world.npc(0).food = 3;
        world.npc(0).wood = 1;

        step(world, Action.DEPOSIT);

        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(10);
        assertThat(world.npc(0).food).isEqualTo(1);
        assertThat(world.npc(0).wood).isEqualTo(1);
    }

    @Test
    void withdrawTakesOnePortionWhileThereIsRoom() {
        World world = world(board -> {
            board.replace(CENTRE, CellType.STORE);
            board.setAmount(CENTRE, 2);
        });
        world.npc(0).wood = 2;

        step(world, Action.WITHDRAW);
        step(world, Action.WITHDRAW);
        step(world, Action.WITHDRAW);

        assertThat(world.npc(0).food).isEqualTo(2);
        assertThat(world.snapshot().amountAt(2, 2)).isZero();
        assertThat(world.views().getFirst().observation().hereAmount()).isZero();
    }

    @Test
    void depositAndWithdrawWorkOnlyOnAStore() {
        World world = world(board -> board.replace(CENTRE, CellType.SHELTER));
        world.npc(0).food = 2;

        step(world, Action.DEPOSIT);
        step(world, Action.WITHDRAW);

        assertThat(world.npc(0).food).isEqualTo(2);
        assertThat(world.snapshot().amountAt(2, 2)).isZero();
    }

    @Test
    void storedFoodLastsThroughWinter() {
        World world = world(TestConfigs.emptyBoard().seasons(20), board -> {
            board.replace(CENTRE, CellType.STORE);
            board.setAmount(CENTRE, 7);
        });

        stay(world, 80);

        assertThat(world.snapshot().amountAt(2, 2)).isEqualTo(7);
        assertThat(cell(world)).isEqualTo(CellType.STORE);
    }

    @Test
    void aBuiltShelterKeepsTheColdOut() {
        World world = world(TestConfigs.emptyBoard().seasons(20), board -> { });
        world.npc(0).wood = 3;
        for (int i = 0; i < 6; i++) {
            step(world, Action.BUILD_SHELTER);
        }
        assertThat(cell(world)).isEqualTo(CellType.SHELTER);

        stay(world, 60 - 6 + 10);

        // Ten winter ticks on the shelter: no cold, and energy came back during the year.
        assertThat(world.npc(0).energy).isEqualTo(100);
    }

    @Test
    void buildingWhereWildFoodWasEatenCancelsItsRegrowth() {
        World world = world(board -> board.set(CENTRE, CellType.FOOD));

        step(world, Action.GATHER);
        step(world, Action.BUILD_STORE);
        stay(world, 6);

        assertThat(cell(world)).isEqualTo(CellType.SITE_STORE);
    }

    @Test
    void listenersHearAboutFinishedBuildings() {
        World world = world(board -> { });
        List<BuildingCompleted> events = new ArrayList<>();
        world.addListener(new WorldListener() {
            @Override
            public void onLifeEnded(LifeEnded event) {
            }

            @Override
            public void onBuildingCompleted(BuildingCompleted event) {
                events.add(event);
            }
        });
        world.npc(0).wood = 2;

        for (int i = 0; i < 4; i++) {
            step(world, Action.BUILD_STORE);
        }

        assertThat(events).containsExactly(new BuildingCompleted(4, 0, CENTRE, CellType.STORE));
    }

    private static World world(Consumer<Board> setup) {
        return world(TestConfigs.emptyBoard(), setup);
    }

    private static World world(TestConfigs configs, Consumer<Board> setup) {
        Board board = new Board(5, 5);
        setup.accept(board);
        World world = new World(config(configs).build(), board);
        world.npc(0).position = CENTRE;
        return world;
    }

    /** One NPC on a 5x5 board with the test economy. */
    private static TestConfigs config(TestConfigs configs) {
        return configs.size(5).npcs(1).foodRegrowTicks(5).lifeLimit(0).energyRegen(1).economy(TestConfigs.economy());
    }

    private static CellType cell(World world) {
        return world.snapshot().cellAt(CENTRE.x(), CENTRE.y());
    }

    private static void step(World world, Action action) {
        world.step(Map.of(world.npc(0).id, action));
    }

    private static void everyone(World world, Action action) {
        Map<Integer, Action> actions = new HashMap<>();
        world.snapshot().npcs().forEach(npc -> actions.put(npc.id(), action));
        world.step(actions);
    }

    private static void stay(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            step(world, Action.STAY);
        }
    }
}
