package com.pgoogol.npcworld.view;

import com.pgoogol.npcworld.core.World;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;

/**
 * Show mode entry point. A separate launcher class lets the window start with
 * {@code java -jar} and JavaFX on the classpath.
 *
 * <p>Arguments: {@code --config <path>} (default {@code config/stage-1.yaml}),
 * {@code --tps <ticks per second>} (default {@code show.ticks_per_second}),
 * {@code --snapshot <file.png>} with {@code --snapshot-tick <n>} (default 300) to save the window once.
 */
public final class ShowMain {

    private ShowMain() {
    }

    private static final AtomicReference<World> PRELOADED = new AtomicReference<>();

    public static void main(String[] args) {
        Application.launch(ShowApp.class, args);
    }

    /**
     * Opens the window on a world that already exists, for example a save loaded from the chronicle.
     * {@code args} are the usual ones; {@code --config} must name the configuration the world was made with.
     */
    public static void show(World world, String[] args) {
        PRELOADED.set(world);
        Application.launch(ShowApp.class, args);
    }

    static Optional<World> takePreloaded() {
        return Optional.ofNullable(PRELOADED.getAndSet(null));
    }
}
