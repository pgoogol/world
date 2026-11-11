package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import com.pgoogol.npcworld.core.WorldListener;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One CSV row per world event, so the learning curve of every single NPC can be rebuilt:
 * <ul>
 *   <li>{@code episode}: a life episode ended (collapse or life limit); {@code value} is its length,</li>
 *   <li>{@code born}: a newborn appeared; {@code value} is the id of the NPC it was born next to (-1 = none),</li>
 *   <li>{@code retired}: an NPC died of old age; {@code value} is unused (0).</li>
 * </ul>
 * {@code npc_age} is the NPC's age in ticks since birth at the event.
 */
public final class EpisodeLog implements WorldListener, AutoCloseable {

    public static final String CSV_HEADER = "tick,event,npc_id,generation,npc_age,value";

    private final BufferedWriter writer;

    public EpisodeLog(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        writer = Files.newBufferedWriter(file);
        writer.write(CSV_HEADER);
        writer.write('\n');
    }

    @Override
    public void onLifeEnded(LifeEnded event) {
        write(event.tick(), "episode", event.npcId(), event.generation(), event.npcAge(), event.lifeLength());
    }

    @Override
    public void onNpcBorn(NpcBorn event) {
        write(event.tick(), "born", event.npcId(), event.generation(), 0, event.parentId().orElse(-1));
    }

    @Override
    public void onNpcRetired(NpcRetired event) {
        write(event.tick(), "retired", event.npcId(), event.generation(), event.totalAge(), 0);
    }

    private void write(long tick, String kind, int npcId, int generation, long npcAge, long value) {
        try {
            writer.write(tick + "," + kind + "," + npcId + "," + generation + "," + npcAge + "," + value);
            writer.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
