package com.pgoogol.npcworld.view;

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

    public static void main(String[] args) {
        Application.launch(ShowApp.class, args);
    }
}
