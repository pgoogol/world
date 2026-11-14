package com.pgoogol.npcworld.history;

import com.pgoogol.npcworld.core.WorldState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import tools.jackson.databind.json.JsonMapper;

/** The chronicle in PostgreSQL: runs, their events and their saved world states. */
public class Chronicle {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public Chronicle(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** A saved state with the run it belongs to. */
    public record Save(long id, long runId, long tick, WorldState state) {
    }

    /** A run as stored in the chronicle. */
    public record Run(long id, String configPath, long seed, int seasonTicks) {
    }

    public long startRun(String configPath, long seed, int seasonTicks) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("insert into run (config_path, seed, season_ticks) values (?, ?, ?)")
                .params(configPath, seed, seasonTicks)
                .update(key, "id");
        return key.getKey().longValue();
    }

    public Optional<Run> run(long runId) {
        return jdbc.sql("select id, config_path, seed, season_ticks from run where id = ?")
                .param(runId)
                .query((rs, row) -> new Run(rs.getLong("id"), rs.getString("config_path"), rs.getLong("seed"),
                        rs.getInt("season_ticks")))
                .optional();
    }

    public List<Run> runs() {
        return jdbc.sql("select id, config_path, seed, season_ticks from run order by id")
                .query((rs, row) -> new Run(rs.getLong("id"), rs.getString("config_path"), rs.getLong("seed"),
                        rs.getInt("season_ticks")))
                .list();
    }

    /** Writes a batch of events in one round trip. */
    public void append(long runId, List<ChronicleEvent> events) {
        template.batchUpdate(
                "insert into event (run_id, tick, kind, npc_id, generation, npc_age, value, x, y, detail) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                events, events.size(), (statement, event) -> {
                    statement.setLong(1, runId);
                    statement.setLong(2, event.tick());
                    statement.setString(3, event.kind());
                    statement.setObject(4, event.npcId());
                    statement.setObject(5, event.generation());
                    statement.setObject(6, event.npcAge());
                    statement.setObject(7, event.value());
                    statement.setObject(8, event.x());
                    statement.setObject(9, event.y());
                    statement.setString(10, event.detail());
                });
    }

    /** Everything that happened to one NPC, in order. */
    public List<ChronicleEvent> npcHistory(long runId, int npcId) {
        return jdbc.sql("select tick, kind, npc_id, generation, npc_age, value, x, y, detail from event "
                        + "where run_id = ? and npc_id = ? order by tick, id")
                .params(runId, npcId)
                .query(Chronicle::event)
                .list();
    }

    /** Counts of what happened in one year (years count from 0). */
    public YearSummary year(long runId, long year) {
        int seasonTicks = run(runId).orElseThrow(() -> new IllegalArgumentException("No run " + runId)).seasonTicks();
        long from = year * 4L * seasonTicks;
        long to = from + 4L * seasonTicks;
        Map<String, Long> counts = new java.util.HashMap<>();
        jdbc.sql("select case when kind = 'built' then 'built-' || detail else kind end as k, count(*) as n "
                        + "from event where run_id = ? and tick >= ? and tick < ? group by k")
                .params(runId, from, to)
                .query((rs, row) -> counts.put(rs.getString("k"), rs.getLong("n")))
                .list();
        return new YearSummary(year, from, to, count(counts, "born"), count(counts, "death"),
                count(counts, "old_age"), count(counts, "sown"), count(counts, "harvested"),
                count(counts, "built-shelter"), count(counts, "built-store"));
    }

    /**
     * Loading a save works like in a game: what the run recorded after the saved tick never happened.
     * Removes the events and saves of the run that come after {@code tick}.
     */
    public void rewind(long runId, long tick) {
        jdbc.sql("delete from event where run_id = ? and tick > ?").params(runId, tick).update();
        jdbc.sql("delete from world_save where run_id = ? and tick > ?").params(runId, tick).update();
    }

    public long save(long runId, WorldState state) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("insert into world_save (run_id, tick, state) values (?, ?, cast(? as jsonb))")
                .params(runId, state.tick(), JSON.writeValueAsString(state))
                .update(key, "id");
        return key.getKey().longValue();
    }

    public Optional<Save> load(long saveId) {
        return jdbc.sql("select id, run_id, tick, state from world_save where id = ?")
                .param(saveId)
                .query((rs, row) -> new Save(rs.getLong("id"), rs.getLong("run_id"), rs.getLong("tick"),
                        JSON.readValue(rs.getString("state"), WorldState.class)))
                .optional();
    }

    private static int count(Map<String, Long> counts, String key) {
        return counts.getOrDefault(key, 0L).intValue();
    }

    private static ChronicleEvent event(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ChronicleEvent(rs.getLong("tick"), rs.getString("kind"),
                rs.getObject("npc_id", Integer.class), rs.getObject("generation", Integer.class),
                rs.getObject("npc_age", Long.class), rs.getObject("value", Long.class),
                rs.getObject("x", Integer.class), rs.getObject("y", Integer.class), rs.getString("detail"));
    }
}
