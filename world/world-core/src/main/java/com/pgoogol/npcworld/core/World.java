package com.pgoogol.npcworld.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The referee: owns the board, the NPCs, the clock and the rewards.
 *
 * <p>Order within one tick, for all NPCs before the next phase starts:
 * <ol>
 *   <li>action: move (a wall blocks it) or eat/drink on the current cell,</li>
 *   <li>hunger and thirst grow,</li>
 *   <li>energy drops for every need at or above the threshold and for standing on lava,</li>
 *   <li>the clock advances and eaten food whose time has come grows back,</li>
 *   <li>outcome: death at zero energy (penalty, respawn) or, at the life limit, truncation
 *       (no penalty, reset); the reward for the tick is set here.</li>
 * </ol>
 */
public final class World {

    private final WorldConfig config;
    private final Random random;
    private final Board board;
    private final List<Npc> npcs = new ArrayList<>();
    private final List<WorldListener> listeners = new ArrayList<>();
    private long tick;

    /** Board and spawn positions come from the seed in the config. */
    public World(WorldConfig config) {
        this(config, new Random(config.seed()), null);
    }

    /** For tests: a hand-made board instead of a generated one. */
    World(WorldConfig config, Board board) {
        this(config, new Random(config.seed()), board);
    }

    private World(WorldConfig config, Random random, Board board) {
        this.config = config;
        this.random = random;
        this.board = board != null ? board : BoardGenerator.generate(config, random);
        for (int id = 0; id < config.npcs(); id++) {
            Npc npc = new Npc(id);
            respawn(npc);
            npcs.add(npc);
        }
    }

    public void addListener(WorldListener listener) {
        listeners.add(listener);
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
        double[] consumeRewards = new double[npcs.size()];
        for (int i = 0; i < npcs.size(); i++) {
            Npc npc = npcs.get(i);
            Action action = actions.get(npc.id);
            if (action == null) {
                throw new IllegalArgumentException("No action for NPC " + npc.id + " at tick " + tick);
            }
            consumeRewards[i] = act(npc, action);
            growNeeds(npc);
            drainEnergy(npc);
            npc.age++;
        }

        tick++;
        board.regrow(tick);

        for (int i = 0; i < npcs.size(); i++) {
            settle(npcs.get(i), consumeRewards[i]);
        }
    }

    public WorldSnapshot snapshot() {
        return new WorldSnapshot(tick, board.width(), board.height(), board.cells(),
                npcs.stream().map(Npc::snapshot).toList());
    }

    /** For tests: direct access to an NPC's state. */
    Npc npc(int id) {
        return npcs.get(id);
    }

    /** Returns the reward earned by eating or drinking, zero for any other action. */
    private double act(Npc npc, Action action) {
        if (action != Action.CONSUME) {
            Position target = npc.position.moved(action);
            if (board.get(target) != CellType.WALL) {
                npc.position = target;
            }
            return 0;
        }

        RewardConfig rewards = config.rewards();
        CellType cell = board.get(npc.position);
        if (cell == CellType.FOOD) {
            double reward = npc.hunger >= rewards.consumeNeedMin() ? rewards.consume() : 0;
            npc.hunger = Math.max(0, npc.hunger - config.consumeRelief());
            board.eatFood(npc.position, tick + config.foodRegrowTicks());
            return reward;
        }
        if (cell == CellType.WATER) {
            double reward = npc.thirst >= rewards.consumeNeedMin() ? rewards.consume() : 0;
            npc.thirst = Math.max(0, npc.thirst - config.consumeRelief());
            return reward;
        }
        return 0;
    }

    private void growNeeds(Npc npc) {
        npc.hunger = Math.min(config.needMax(), npc.hunger + config.needPerTick());
        npc.thirst = Math.min(config.needMax(), npc.thirst + config.needPerTick());
    }

    private void drainEnergy(Npc npc) {
        int loss = 0;
        if (npc.hunger >= config.needThreshold()) {
            loss += config.needEnergyLoss();
        }
        if (npc.thirst >= config.needThreshold()) {
            loss += config.needEnergyLoss();
        }
        if (board.get(npc.position) == CellType.LAVA) {
            loss += config.lavaEnergyLoss();
        }
        if (loss > 0) {
            npc.energy = Math.max(0, npc.energy - loss);
        } else {
            npc.energy = Math.min(config.energyMax(), npc.energy + config.energyRegen());
        }
    }

    private void settle(Npc npc, double consumeReward) {
        npc.died = false;
        npc.truncated = false;
        npc.finalObservation = null;

        if (npc.energy <= 0) {
            npc.reward = config.rewards().death();
            npc.died = true;
            endLife(npc, LifeEnded.Cause.DEATH);
            return;
        }

        npc.reward = config.rewards().perTick() + consumeReward;
        if (npc.age >= config.lifeLimit()) {
            npc.truncated = true;
            npc.finalObservation = observe(npc);
            endLife(npc, LifeEnded.Cause.LIFE_LIMIT);
        }
    }

    private void endLife(Npc npc, LifeEnded.Cause cause) {
        LifeEnded event = new LifeEnded(npc.id, tick, npc.age, cause);
        respawn(npc);
        listeners.forEach(listener -> listener.onLifeEnded(event));
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

    /** Puts the NPC on a random safe cell with full energy and no needs. */
    private void respawn(Npc npc) {
        int x;
        int y;
        do {
            x = 1 + random.nextInt(config.width() - 2);
            y = 1 + random.nextInt(config.height() - 2);
        } while (!board.isSafe(x, y));
        npc.position = new Position(x, y);
        npc.hunger = 0;
        npc.thirst = 0;
        npc.energy = config.energyMax();
        npc.age = 0;
    }
}
