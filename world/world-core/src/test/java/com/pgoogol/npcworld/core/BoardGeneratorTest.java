package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BoardGeneratorTest {

    @Test
    void boardHasTheConfiguredSharesInsideTheWall() {
        WorldConfig config = TestConfigs.stage1().build();

        List<CellType> cells = BoardGenerator.generate(config, new Random(config.seed())).cells();

        // 18x18 = 324 cells inside the wall: 8% food, 5% water, 5% lava.
        assertThat(cells).filteredOn(cell -> cell == CellType.FOOD).hasSize(26);
        assertThat(cells).filteredOn(cell -> cell == CellType.WATER).hasSize(16);
        assertThat(cells).filteredOn(cell -> cell == CellType.LAVA).hasSize(16);
        assertThat(cells).filteredOn(cell -> cell == CellType.WALL).hasSize(76);
    }

    @Test
    void wallSurroundsTheBoard() {
        WorldConfig config = TestConfigs.stage1().build();

        Board board = BoardGenerator.generate(config, new Random(config.seed()));

        for (int i = 0; i < 20; i++) {
            assertThat(board.get(i, 0)).isEqualTo(CellType.WALL);
            assertThat(board.get(i, 19)).isEqualTo(CellType.WALL);
            assertThat(board.get(0, i)).isEqualTo(CellType.WALL);
            assertThat(board.get(19, i)).isEqualTo(CellType.WALL);
        }
    }

    @Test
    void everySafeCellIsReachableWithoutCrossingLava() {
        for (long seed = 0; seed < 50; seed++) {
            WorldConfig config = TestConfigs.stage1().seed(seed).build();

            Board board = BoardGenerator.generate(config, new Random(seed));

            assertThat(BoardGenerator.safeCellsAreConnected(board)).isTrue();
        }
    }

    @Test
    void connectivityCheckSpotsACellCutOffByLava() {
        Board board = new Board(5, 5);
        board.set(2, 1, CellType.LAVA);
        board.set(1, 2, CellType.LAVA);
        assertThat(BoardGenerator.safeCellsAreConnected(board)).isFalse();

        board.set(1, 2, CellType.EMPTY);
        assertThat(BoardGenerator.safeCellsAreConnected(board)).isTrue();
    }

    @Test
    void sameSeedGivesTheSameBoard() {
        WorldConfig config = TestConfigs.stage1().build();

        assertThat(BoardGenerator.generate(config, new Random(7)).cells())
                .isEqualTo(BoardGenerator.generate(config, new Random(7)).cells());
        assertThat(BoardGenerator.generate(config, new Random(7)).cells())
                .isNotEqualTo(BoardGenerator.generate(config, new Random(8)).cells());
    }
}
