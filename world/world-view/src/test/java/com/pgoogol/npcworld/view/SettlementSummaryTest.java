package com.pgoogol.npcworld.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Position;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.WorldSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementSummaryTest {

    @Test
    void summaryCountsBuildingsFieldsStockAndWhatNpcsCarry() {
        SettlementSummary summary = SettlementSummary.of(winter(2250));

        assertThat(summary).isEqualTo(new SettlementSummary(3, 42, 1, 2, 1, 1, 1, 3, 4, 2));
    }

    @Test
    void calendarCountsYearsFromOneAndTicksToTheEndOfTheSeason() {
        assertThat(SettlementSummary.calendar(winter(2250), 500)).isEqualTo("Rok 2, zima, do końca pory 250 tur");
        assertThat(SettlementSummary.calendar(snapshot(0, Season.SPRING), 500))
                .isEqualTo("Rok 1, wiosna, do końca pory 500 tur");
    }

    @Test
    void statusTextShowsTheSeasonTheStockAndTheInventory() {
        String text = ShowApp.describeSettlement(winter(2250), 500);

        assertThat(text).contains("Rok 2, zima", "Mieszkańcy: 3 (w schronieniach: 2)", "Zapas w magazynach: 42 porcji",
                "Schronienia: 1, magazyny: 2, place budowy: 1", "Pola zasiane: 1, dojrzałe: 1",
                "Niesione: 3 porcji jedzenia, 4 drewna");
    }

    @Test
    void bigBoardGetsSmallerCells() {
        assertThat(BoardPainter.cellSize(20, 20)).isEqualTo(BoardPainter.CELL);
        assertThat(BoardPainter.cellSize(64, 64)).isEqualTo(14);
    }

    @Test
    void groundTellsTheSeason() {
        assertThat(BoardPainter.groundColor(Season.WINTER)).isNotEqualTo(BoardPainter.groundColor(Season.SUMMER));
        assertThat(BoardPainter.groundColor(Season.AUTUMN)).isNotEqualTo(BoardPainter.groundColor(Season.SUMMER));
        assertThat(BoardPainter.groundColor(null)).isEqualTo(BoardPainter.groundColor(Season.SPRING));
    }

    private static WorldSnapshot winter(long tick) {
        return snapshot(tick, Season.WINTER);
    }

    /** A 4x2 board: two stores, a shelter with two NPCs, a site, two fields and one NPC outside. */
    private static WorldSnapshot snapshot(long tick, Season season) {
        return new WorldSnapshot(tick, season, 4, 2,
                List.of(CellType.STORE, CellType.STORE, CellType.SHELTER, CellType.SITE_STORE,
                        CellType.FIELD_SOWN, CellType.FIELD_RIPE, CellType.EMPTY, CellType.TREE),
                List.of(40, 2, 0, 3, 0, 4, 0, 0),
                List.of(npc(1, 2, 0, 2, 0), npc(2, 2, 0, 0, 4), npc(3, 2, 1, 1, 0)));
    }

    private static NpcSnapshot npc(int id, int x, int y, int food, int wood) {
        return new NpcSnapshot(id, new Position(x, y), 0, 0, 100, 0, 0, 0, 0, food, wood);
    }
}
