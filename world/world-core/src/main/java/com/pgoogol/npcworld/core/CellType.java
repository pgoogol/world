package com.pgoogol.npcworld.core;

public enum CellType {
    EMPTY,
    FOOD,
    WATER,
    LAVA,
    WALL,
    // Stage 3.
    TREE,
    /** A felled tree; grows back into a tree. */
    STUMP,
    FIELD_SOWN,
    FIELD_RIPE,
    SITE_SHELTER,
    SITE_STORE,
    SHELTER,
    STORE
}
