package com.pgoogol.npcworld.view;

import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.WorldSnapshot;

/** Stage 3: the settlement in numbers, counted from one snapshot. The view shows it next to the board. */
record SettlementSummary(
        int population, int storedFood, int shelters, int stores, int sites, int fieldsSown, int fieldsRipe,
        int carriedFood, int carriedWood, int inShelters) {

    static SettlementSummary of(WorldSnapshot snapshot) {
        int storedFood = 0;
        int shelters = 0;
        int stores = 0;
        int sites = 0;
        int fieldsSown = 0;
        int fieldsRipe = 0;
        for (int index = 0; index < snapshot.cells().size(); index++) {
            switch (snapshot.cells().get(index)) {
                case STORE -> {
                    stores++;
                    storedFood += snapshot.amounts().get(index);
                }
                case SHELTER -> shelters++;
                case SITE_SHELTER, SITE_STORE -> sites++;
                case FIELD_SOWN -> fieldsSown++;
                case FIELD_RIPE -> fieldsRipe++;
                default -> {
                }
            }
        }
        int carriedFood = 0;
        int carriedWood = 0;
        int inShelters = 0;
        for (NpcSnapshot npc : snapshot.npcs()) {
            carriedFood += npc.carriedFood();
            carriedWood += npc.carriedWood();
            if (snapshot.cellAt(npc.position().x(), npc.position().y()) == CellType.SHELTER) {
                inShelters++;
            }
        }
        return new SettlementSummary(snapshot.npcs().size(), storedFood, shelters, stores, sites, fieldsSown,
                fieldsRipe, carriedFood, carriedWood, inShelters);
    }

    /** "Rok 2, jesień, do końca pory 120 tur"; years count from one on the screen. */
    static String calendar(WorldSnapshot snapshot, int seasonTicks) {
        long year = snapshot.tick() / (4L * seasonTicks) + 1;
        long left = seasonTicks - snapshot.tick() % seasonTicks;
        return "Rok " + year + ", " + seasonName(snapshot.season()) + ", do końca pory " + left + " tur";
    }

    static String seasonName(Season season) {
        return switch (season) {
            case SPRING -> "wiosna";
            case SUMMER -> "lato";
            case AUTUMN -> "jesień";
            case WINTER -> "zima";
        };
    }
}
