-- Stage 3: the chronicle of a world and its saved states.

-- One simulation run: which configuration it used and how long its seasons are.
create table run (
    id           bigserial primary key,
    started_at   timestamptz not null default now(),
    config_path  text        not null,
    seed         bigint      not null,
    season_ticks int         not null
);

-- Everything that happened in a run. Columns that do not apply to a kind of event stay empty.
--   death     an NPC died for good; value = ticks since its last collapse
--   episode   stage 1 and 2: a life episode ended (collapse or life limit); value = its length
--   born      a newborn appeared; value = the NPC it was born next to
--   old_age   an NPC died of old age
--   season    the season changed; value = year, detail = season
--   sown      an NPC sowed a field at (x, y)
--   harvested an NPC took the last portion from a field at (x, y)
--   built     an NPC finished a building at (x, y); detail = shelter or store
create table event (
    id         bigserial primary key,
    run_id     bigint not null references run (id),
    tick       bigint not null,
    kind       text   not null,
    npc_id     int,
    generation int,
    npc_age    bigint,
    value      bigint,
    x          int,
    y          int,
    detail     text
);

create index event_by_npc on event (run_id, npc_id, tick);
create index event_by_tick on event (run_id, tick);

-- A world saved like a game: the complete state as JSON (see WorldState in world-core).
create table world_save (
    id       bigserial primary key,
    run_id   bigint      not null references run (id),
    tick     bigint      not null,
    saved_at timestamptz not null default now(),
    state    jsonb       not null
);

create index world_save_by_run on world_save (run_id, tick);
