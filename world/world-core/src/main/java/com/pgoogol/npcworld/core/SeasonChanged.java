package com.pgoogol.npcworld.core;

/** Stage 3: at {@code tick} the season became {@code season}. {@code year} counts from 0. */
public record SeasonChanged(long tick, Season season, long year) {
}
