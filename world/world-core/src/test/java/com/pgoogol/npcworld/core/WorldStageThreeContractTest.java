package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Step 3.1: settings that reproduce stages 1 and 2 by default, a wider view and trees on the board. */
class WorldStageThreeContractTest {

    @Test
    void stageOneDefaultsSwitchEverythingNewOff() {
        WorldConfig config = TestConfigs.stage1().build();

        assertThat(config.seasons().enabled()).isFalse();
        assertThat(config.economy().inventorySize()).isZero();
        assertThat(config.population().deathIsFinal()).isFalse();
        assertThat(config.population().birthsEnabled()).isFalse();
        assertThat(config.viewRadius()).isEqualTo(1);
        assertThat(config.rewards().winterSurvived()).isZero();
    }

    @Test
    void viewRadiusThreeShowsSevenBySevenWithWallBeyondTheBoard() {
        World world = new World(TestConfigs.emptyBoard().size(8).npcs(1).viewRadius(3).build(), new Board(8, 8));
        world.npc(0).position = new Position(1, 1);

        Observation observation = world.views().getFirst().observation();

        assertThat(observation.surroundings()).hasSize(49);
        assertThat(observation.viewSize()).isEqualTo(7);
        // Row 0 and column 0 of the square lie two cells outside the board: wall.
        assertThat(observation.surroundings().subList(0, 7)).containsOnly(CellType.WALL);
        // The centre is the NPC's own cell.
        assertThat(observation.surroundings().get(24)).isEqualTo(CellType.EMPTY);
    }

    @Test
    void defaultViewIsTheStageOneSquare() {
        World world = new World(TestConfigs.emptyBoard().size(5).npcs(1).build(), new Board(5, 5));

        assertThat(world.views().getFirst().observation().surroundings()).hasSize(9);
    }

    @Test
    void lifeLimitZeroNeverTruncates() {
        World world = new World(TestConfigs.emptyBoard().size(5).npcs(1).lifeLimit(0).build(), new Board(5, 5));
        world.npc(0).position = new Position(2, 2);

        for (int i = 0; i < 50; i++) {
            world.step(java.util.Map.of(0, Action.STAY));
            assertThat(world.views().getFirst().truncated()).isFalse();
        }
        assertThat(world.npc(0).age).isEqualTo(50);
    }

    @Test
    void boardGetsTheConfiguredShareOfTrees() {
        EconomyConfig economy = new EconomyConfig(0.05, 10, 4, 10, 4, 3, 2, 2, 2, 1, 10);
        WorldConfig config = TestConfigs.stage1().economy(economy).build();

        List<CellType> cells = BoardGenerator.generate(config, new Random(config.seed())).cells();

        // 18x18 = 324 cells inside the wall, 5% trees.
        assertThat(cells).filteredOn(cell -> cell == CellType.TREE).hasSize(16);
        assertThat(cells).filteredOn(cell -> cell == CellType.FOOD).hasSize(26);
    }

    @Test
    void treesDoNotChangeWhereEverythingElseLies() {
        WorldConfig plain = TestConfigs.stage1().build();
        WorldConfig wooded = TestConfigs.stage1().economy(new EconomyConfig(0.05, 10, 4, 10, 4, 3, 2, 2, 2, 1, 10)).build();

        List<CellType> before = BoardGenerator.generate(plain, new Random(42)).cells();
        List<CellType> after = BoardGenerator.generate(wooded, new Random(42)).cells();

        for (int index = 0; index < before.size(); index++) {
            if (after.get(index) != CellType.TREE) {
                assertThat(after.get(index)).isEqualTo(before.get(index));
            }
        }
    }

    @Test
    void sharesWithTreesMustLeaveEmptyCells() {
        assertThatThrownBy(() -> TestConfigs.stage1()
                .economy(new EconomyConfig(0.9, 10, 4, 10, 4, 3, 2, 2, 2, 1, 10)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
