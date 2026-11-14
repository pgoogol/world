package com.pgoogol.npcworld.history;

/**
 * One row of the chronicle. Fields that do not apply to a kind of event are null.
 * See {@code V1__chronicle.sql} for what {@code value} and {@code detail} mean per kind.
 */
public record ChronicleEvent(
        long tick, String kind, Integer npcId, Integer generation, Long npcAge, Long value,
        Integer x, Integer y, String detail) {
}
