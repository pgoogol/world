package com.pgoogol.npcworld.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/** The referee: owns the board, the NPCs, the clock and the rewards. */
public final class World {

    private final WorldConfig config;
    private final Random random;
    private final Board board;
    private final List<Npc> npcs = new ArrayList<>();
    private long tick;

    public World(WorldConfig config) {
        this.config = config;
        this.random = new Random(config.seed());
        this.board = new Board(config.width(), config.height());
        for (int id = 0; id < config.npcs(); id++) {
            Npc npc = new Npc(id);
            respawn(npc);
            npcs.add(npc);
        }
    }

    public long tick() {
        return tick;
    }

    /** What every NPC sees right now, together with the outcome of its previous action. */
    public List<NpcView> views() {
        List<NpcView> views = new ArrayList<>(npcs.size());
        for (Npc npc : npcs) {
            views.add(new NpcView(npc.id, observe(npc), npc.reward, npc.died, npc.truncated,
                    Optional.ofNullable(npc.finalObservation)));
        }
        return views;
    }

    /** Applies one action per NPC and advances the clock by one tick. */
    public void step(Map<Integer, Action> actions) {
        for (Npc npc : npcs) {
            Action action = actions.get(npc.id);
            if (action == null) {
                throw new IllegalArgumentException("No action for NPC " + npc.id + " at tick " + tick);
            }
            move(npc, action);
        }
        tick++;
    }

    public WorldSnapshot snapshot() {
        return new WorldSnapshot(tick, board.width(), board.height(), board.cells(),
                npcs.stream().map(Npc::snapshot).toList());
    }

    private void move(Npc npc, Action action) {
        Position target = npc.position.moved(action);
        if (board.get(target) != CellType.WALL) {
            npc.position = target;
        }
    }

    private Observation observe(Npc npc) {
        List<CellType> surroundings = new ArrayList<>(9);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                surroundings.add(board.get(npc.position.x() + dx, npc.position.y() + dy));
            }
        }
        return new Observation(surroundings, npc.hunger, npc.thirst, npc.energy);
    }

    private void respawn(Npc npc) {
        Position position;
        do {
            position = new Position(1 + random.nextInt(config.width() - 2), 1 + random.nextInt(config.height() - 2));
        } while (board.get(position) == CellType.LAVA || board.get(position) == CellType.WALL);
        npc.position = position;
        npc.hunger = 0;
        npc.thirst = 0;
        npc.energy = config.energyMax();
        npc.age = 0;
    }
}
