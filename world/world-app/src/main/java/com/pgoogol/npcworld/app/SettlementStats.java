package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.SeasonConfig;
import com.pgoogol.npcworld.core.WorldListener;
import com.pgoogol.npcworld.core.WorldSnapshot;

/**
 * Stage 3: the state of the settlement at the end of every statistics window. Births and deaths count
 * the events within the window; everything else is what the world looks like when the window closes.
 */
public final class SettlementStats implements WorldListener {

    public static final String CSV_HEADER = "tick,year,season,population,births,deaths,old_age,"
            + "stored_food,carried_food,carried_wood,shelters,stores,sites,fields_sown,fields_ripe,wild_food,trees";

    private final SeasonConfig seasons;
    private int births;
    private int deaths;
    private int oldAge;

    public SettlementStats(SeasonConfig seasons) {
        this.seasons = seasons;
    }

    @Override
    public void onLifeEnded(LifeEnded event) {
        if (event.cause() == LifeEnded.Cause.DEATH) {
            deaths++;
        }
    }

    @Override
    public void onNpcBorn(NpcBorn event) {
        births++;
    }

    @Override
    public void onNpcRetired(NpcRetired event) {
        oldAge++;
    }

    /** The CSV row for the window that closes now; starts counting the next window. */
    public String closeWindow(WorldSnapshot snapshot) {
        int[] cells = new int[CellType.values().length];
        int storedFood = 0;
        for (int index = 0; index < snapshot.cells().size(); index++) {
            CellType cell = snapshot.cells().get(index);
            cells[cell.ordinal()]++;
            if (cell == CellType.STORE) {
                storedFood += snapshot.amounts().get(index);
            }
        }
        int carriedFood = snapshot.npcs().stream().mapToInt(NpcSnapshot::carriedFood).sum();
        int carriedWood = snapshot.npcs().stream().mapToInt(NpcSnapshot::carriedWood).sum();
        long year = seasons.enabled() ? snapshot.tick() / (4L * seasons.seasonTicks()) : 0;
        String season = snapshot.season() == null ? "" : snapshot.season().name().toLowerCase();

        String row = String.join(",",
                Long.toString(snapshot.tick()), Long.toString(year), season,
                Integer.toString(snapshot.npcs().size()), Integer.toString(births), Integer.toString(deaths),
                Integer.toString(oldAge), Integer.toString(storedFood), Integer.toString(carriedFood),
                Integer.toString(carriedWood),
                Integer.toString(cells[CellType.SHELTER.ordinal()]), Integer.toString(cells[CellType.STORE.ordinal()]),
                Integer.toString(cells[CellType.SITE_SHELTER.ordinal()] + cells[CellType.SITE_STORE.ordinal()]),
                Integer.toString(cells[CellType.FIELD_SOWN.ordinal()]), Integer.toString(cells[CellType.FIELD_RIPE.ordinal()]),
                Integer.toString(cells[CellType.FOOD.ordinal()]), Integer.toString(cells[CellType.TREE.ordinal()]));
        births = 0;
        deaths = 0;
        oldAge = 0;
        return row;
    }
}
