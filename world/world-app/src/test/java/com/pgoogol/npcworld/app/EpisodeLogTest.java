package com.pgoogol.npcworld.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcBorn;
import com.pgoogol.npcworld.core.NpcRetired;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpisodeLogTest {

    @Test
    void writesOneRowPerEvent(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("nested").resolve("episodes.csv");

        try (EpisodeLog log = new EpisodeLog(file)) {
            log.onLifeEnded(new LifeEnded(3, 700, 120, LifeEnded.Cause.DEATH, 0, 650));
            log.onNpcRetired(new NpcRetired(3, 900, 850, 0));
            log.onNpcBorn(new NpcBorn(12, 900, 1, Optional.of(5)));
            log.onNpcBorn(new NpcBorn(13, 950, 2, Optional.empty()));
        }

        assertThat(Files.readAllLines(file)).containsExactly(
                "tick,event,npc_id,generation,npc_age,value",
                "700,episode,3,0,650,120",
                "900,retired,3,0,850,0",
                "900,born,12,1,0,5",
                "950,born,13,2,0,-1");
    }
}
