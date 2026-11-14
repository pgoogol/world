package com.pgoogol.npcworld.history;

/** What happened in one year of a run, counted from the chronicle. Years count from 0. */
public record YearSummary(
        long year, long fromTick, long toTick, int births, int deaths, int oldAge,
        int sown, int harvested, int shelters, int stores) {
}
