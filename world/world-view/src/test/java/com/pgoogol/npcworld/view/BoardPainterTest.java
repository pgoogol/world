package com.pgoogol.npcworld.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Position;
import org.junit.jupiter.api.Test;

class BoardPainterTest {

    @Test
    void npcsInAdjacentCellsAreNeighbours() {
        assertThat(BoardPainter.areNeighbours(npc(0, 2, 2, 0, 0), npc(1, 3, 3, 0, 0))).isTrue();
        assertThat(BoardPainter.areNeighbours(npc(0, 2, 2, 0, 0), npc(1, 2, 2, 0, 0))).isTrue();
        assertThat(BoardPainter.areNeighbours(npc(0, 2, 2, 0, 0), npc(1, 4, 2, 0, 0))).isFalse();
    }

    @Test
    void ageShareIsZeroWithoutALifespan() {
        assertThat(BoardPainter.ageShare(npc(0, 1, 1, 500, 0))).isZero();
        assertThat(BoardPainter.ageShare(npc(0, 1, 1, 500, 2000))).isEqualTo(0.25);
    }

    private static NpcSnapshot npc(int id, int x, int y, long totalAge, long lifespan) {
        return new NpcSnapshot(id, new Position(x, y), 0, 0, 100, 0, totalAge, lifespan, 0);
    }
}
