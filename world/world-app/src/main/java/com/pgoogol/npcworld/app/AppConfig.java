package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.WorldConfig;

/** Everything the Java side reads from the stage configuration file. */
public record AppConfig(
        WorldConfig world,
        String brainAddress,
        long trainingTicks,
        int statsWindow,
        String outDir,
        double ticksPerSecond) {
}
