package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.WorldListener;
import java.util.Locale;
import java.util.Optional;

/**
 * Average life length in windows of a fixed number of ticks. A life counts in the window
 * in which it ended, whether by death or by reaching the life limit.
 */
public final class LifeStats implements WorldListener {

    public static final String CSV_HEADER = "tick,lives_ended,mean_life_length";

    /** One window. {@code tick} is the tick at which the window closed. */
    public record Row(long tick, int livesEnded, long lifeLengthSum) {

        /** NaN when no life ended in the window. */
        public double meanLifeLength() {
            return livesEnded == 0 ? Double.NaN : (double) lifeLengthSum / livesEnded;
        }

        /** CSV line with a dot as the decimal separator; the mean is empty when no life ended. */
        public String toCsv() {
            String mean = livesEnded == 0 ? "" : String.format(Locale.ROOT, "%.2f", meanLifeLength());
            return tick + "," + livesEnded + "," + mean;
        }
    }

    private final int window;
    private int livesEnded;
    private long lifeLengthSum;
    private int totalLives;
    private long totalLifeLength;

    public LifeStats(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("Stats window must be at least one tick");
        }
        this.window = window;
    }

    @Override
    public void onLifeEnded(LifeEnded event) {
        livesEnded++;
        lifeLengthSum += event.lifeLength();
        totalLives++;
        totalLifeLength += event.lifeLength();
    }

    /** Call after every tick with the world's clock. Returns a row when a window has just closed. */
    public Optional<Row> closeWindowIfDue(long tick) {
        if (tick % window != 0) {
            return Optional.empty();
        }
        Row row = new Row(tick, livesEnded, lifeLengthSum);
        livesEnded = 0;
        lifeLengthSum = 0;
        return Optional.of(row);
    }

    public int totalLives() {
        return totalLives;
    }

    /** Mean over every life ended so far; NaN before the first one. */
    public double overallMeanLifeLength() {
        return totalLives == 0 ? Double.NaN : (double) totalLifeLength / totalLives;
    }
}
