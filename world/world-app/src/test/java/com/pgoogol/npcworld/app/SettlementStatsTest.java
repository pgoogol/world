package com.pgoogol.npcworld.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Position;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.SeasonConfig;
import com.pgoogol.npcworld.core.WorldSnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementStatsTest {

    @Test
    void rowCountsEventsInTheWindowAndTheStateAtItsEnd() {
        SettlementStats stats = new SettlementStats(new SeasonConfig(500, List.of(100, 100, 50, 0), 1, true));
        stats.onNpcBorn(new NpcBorn(7, 10, 1, Optional.of(1)));
        stats.onLifeEnded(new LifeEnded(2, 20, 300, LifeEnded.Cause.DEATH, 0, 300));
        stats.onNpcRetired(new NpcRetired(3, 30, 6000, 0));
        WorldSnapshot snapshot = new WorldSnapshot(2250, Season.WINTER, 2, 3,
                List.of(CellType.STORE, CellType.STORE, CellType.SHELTER, CellType.FIELD_SOWN, CellType.FOOD,
                        CellType.SITE_STORE),
                List.of(40, 2, 0, 0, 0, 1),
                List.of(npc(1, 2, 0), npc(7, 1, 3)));

        String row = stats.closeWindow(snapshot);

        assertThat(SettlementStats.CSV_HEADER.split(",")).hasSize(row.split(",").length);
        assertThat(row).isEqualTo("2250,1,winter,2,1,1,1,42,3,3,1,2,1,1,0,1,0");
        assertThat(stats.closeWindow(snapshot)).startsWith("2250,1,winter,2,0,0,0,");
    }

    private static NpcSnapshot npc(int id, int food, int wood) {
        return new NpcSnapshot(id, new Position(0, 0), 0, 0, 100, 0, 0, 0, 0, food, wood);
    }
}
