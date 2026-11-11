package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.WorldSnapshot;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the simulation on its own thread at a fixed pace and publishes an immutable snapshot
 * after every tick. A view reads {@link #latest()} and never touches the world itself.
 */
public final class SimulationRunner implements AutoCloseable {

    private final Simulation simulation;
    private final long periodNanos;
    private final ScheduledExecutorService executor;
    private final AtomicReference<WorldSnapshot> latest;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    public SimulationRunner(Simulation simulation, double ticksPerSecond) {
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("Ticks per second must be positive");
        }
        this.simulation = simulation;
        this.periodNanos = Math.max(1, Math.round(1_000_000_000 / ticksPerSecond));
        this.latest = new AtomicReference<>(simulation.world().snapshot());
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "simulation");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        executor.scheduleAtFixedRate(this::tick, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    private void tick() {
        try {
            simulation.tick();
            latest.set(simulation.world().snapshot());
        } catch (RuntimeException e) {
            failure.set(e);
            // Rethrowing cancels further ticks: a broken run must not continue silently.
            throw e;
        }
    }

    /** The world as it was after the most recent tick. */
    public WorldSnapshot latest() {
        return latest.get();
    }

    /** Present when a tick failed, for example because the brains server went away. */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure.get());
    }

    @Override
    public void close() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
