package com.pgoogol.npcworld.core;

/** Receives world events. Listeners only observe: they must not change the world. */
public interface WorldListener {

    void onLifeEnded(LifeEnded event);

    default void onNpcRetired(NpcRetired event) {
    }

    default void onNpcBorn(NpcBorn event) {
    }

    default void onSeasonChanged(SeasonChanged event) {
    }

    default void onFieldSown(FieldSown event) {
    }

    default void onFieldHarvested(FieldHarvested event) {
    }

    default void onBuildingCompleted(BuildingCompleted event) {
    }
}
