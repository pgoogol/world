package com.pgoogol.npcworld.history;

import com.pgoogol.npcworld.core.BuildingCompleted;
import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.FieldHarvested;
import com.pgoogol.npcworld.core.FieldSown;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import com.pgoogol.npcworld.core.SeasonChanged;
import com.pgoogol.npcworld.core.WorldListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listens to the world and writes its events to the chronicle in batches, on a background thread,
 * so the simulation never waits for the database. {@link #close()} writes what is left and reports
 * any failure of the background writes.
 */
public final class ChronicleWriter implements WorldListener, AutoCloseable {

    private final Chronicle chronicle;
    private final long runId;
    private final int batchSize;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "chronicle-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    private List<ChronicleEvent> batch = new ArrayList<>();

    public ChronicleWriter(Chronicle chronicle, long runId, int batchSize) {
        this.chronicle = chronicle;
        this.runId = runId;
        this.batchSize = batchSize;
    }

    @Override
    public void onLifeEnded(LifeEnded event) {
        String kind = event.cause() == LifeEnded.Cause.DEATH ? "death" : "episode";
        add(new ChronicleEvent(event.tick(), kind, event.npcId(), event.generation(), event.npcAge(),
                (long) event.lifeLength(), null, null, null));
    }

    @Override
    public void onNpcBorn(NpcBorn event) {
        add(new ChronicleEvent(event.tick(), "born", event.npcId(), event.generation(), 0L,
                event.parentId().map(Integer::longValue).orElse(null), null, null, null));
    }

    @Override
    public void onNpcRetired(NpcRetired event) {
        add(new ChronicleEvent(event.tick(), "old_age", event.npcId(), event.generation(), event.totalAge(),
                null, null, null, null));
    }

    @Override
    public void onSeasonChanged(SeasonChanged event) {
        add(new ChronicleEvent(event.tick(), "season", null, null, null, event.year(), null, null,
                event.season().name().toLowerCase()));
    }

    @Override
    public void onFieldSown(FieldSown event) {
        add(new ChronicleEvent(event.tick(), "sown", event.npcId(), null, null, null,
                event.position().x(), event.position().y(), null));
    }

    @Override
    public void onFieldHarvested(FieldHarvested event) {
        add(new ChronicleEvent(event.tick(), "harvested", event.npcId(), null, null, null,
                event.position().x(), event.position().y(), null));
    }

    @Override
    public void onBuildingCompleted(BuildingCompleted event) {
        String type = event.type() == CellType.SHELTER ? "shelter" : "store";
        add(new ChronicleEvent(event.tick(), "built", event.npcId(), null, null, null,
                event.position().x(), event.position().y(), type));
    }

    private void add(ChronicleEvent event) {
        batch.add(event);
        if (batch.size() >= batchSize) {
            flush();
        }
    }

    /** Hands the collected events to the background thread. */
    public void flush() {
        if (batch.isEmpty()) {
            return;
        }
        List<ChronicleEvent> full = batch;
        batch = new ArrayList<>();
        writer.execute(() -> {
            try {
                chronicle.append(runId, full);
            } catch (RuntimeException e) {
                failure.compareAndSet(null, e);
            }
        });
    }

    @Override
    public void close() throws InterruptedException {
        flush();
        writer.shutdown();
        if (!writer.awaitTermination(1, TimeUnit.MINUTES)) {
            throw new IllegalStateException("The chronicle did not finish writing within a minute");
        }
        RuntimeException error = failure.get();
        if (error != null) {
            throw new IllegalStateException("Writing the chronicle failed", error);
        }
    }
}
