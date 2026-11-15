package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.BuildingCompleted;
import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.FieldHarvested;
import com.pgoogol.npcworld.core.FieldSown;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.WorldListener;
import com.pgoogol.npcworld.core.WorldSnapshot;

/**
 * Stage 3, training in episodes: what one settlement did between its start and its end. One row per
 * episode makes the learning curve: how long settlements last and whether they sow, store and build.
 */
public final class EpisodeStats implements WorldListener {

    public static final String CSV_HEADER = "episode,end_tick,ticks,winters,population,births,deaths,old_age,"
            + "fields_sown,fields_harvested,shelters_built,stores_built,stored_food_winter,population_winter";

    private int winters;
    private int births;
    private int deaths;
    private int oldAge;
    private int fieldsSown;
    private int fieldsHarvested;
    private int sheltersBuilt;
    private int storesBuilt;
    private int storedFoodWinter;
    private int populationWinter;

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

    @Override
    public void onFieldSown(FieldSown event) {
        fieldsSown++;
    }

    @Override
    public void onFieldHarvested(FieldHarvested event) {
        fieldsHarvested++;
    }

    @Override
    public void onBuildingCompleted(BuildingCompleted event) {
        if (event.type() == CellType.SHELTER) {
            sheltersBuilt++;
        } else if (event.type() == CellType.STORE) {
            storesBuilt++;
        }
    }

    /**
     * Call on the first tick of every season. A winter counts as survived when somebody sees the
     * spring; the stores and the population are noted on the first day of the latest winter.
     */
    public void seasonStarted(WorldSnapshot snapshot) {
        if (snapshot.season() == Season.WINTER) {
            populationWinter = snapshot.npcs().size();
            storedFoodWinter = 0;
            for (int index = 0; index < snapshot.cells().size(); index++) {
                if (snapshot.cells().get(index) == CellType.STORE) {
                    storedFoodWinter += snapshot.amounts().get(index);
                }
            }
        } else if (snapshot.season() == Season.SPRING && snapshot.tick() > 0 && !snapshot.npcs().isEmpty()) {
            winters++;
        }
    }

    /** The CSV row of the episode that ends now. */
    public String close(int episode, long endTick, long ticks, int population) {
        return String.join(",",
                Integer.toString(episode), Long.toString(endTick), Long.toString(ticks), Integer.toString(winters),
                Integer.toString(population), Integer.toString(births), Integer.toString(deaths),
                Integer.toString(oldAge), Integer.toString(fieldsSown), Integer.toString(fieldsHarvested),
                Integer.toString(sheltersBuilt), Integer.toString(storesBuilt), Integer.toString(storedFoodWinter),
                Integer.toString(populationWinter));
    }
}
