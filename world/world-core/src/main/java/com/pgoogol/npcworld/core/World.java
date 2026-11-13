package com.pgoogol.npcworld.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The referee: owns the board, the NPCs, the clock and the rewards.
 *
 * <p>Order within one tick, for all NPCs before the next phase starts:
 * <ol>
 *   <li>action: move (a wall blocks it) or eat/drink on the current cell,</li>
 *   <li>hunger and thirst grow, the NPC gets one tick older,</li>
 *   <li>energy drops for every need at or above the threshold and for standing on lava,</li>
 *   <li>the clock advances and eaten food whose time has come grows back,</li>
 *   <li>outcome: collapse at zero energy (penalty, respawn with the same memory) or, at the life limit,
 *       truncation (no penalty, reset); the reward for the tick is set here,</li>
 *   <li>old age: an NPC that reached its lifespan is gone for good and a newborn takes its place.</li>
 * </ol>
 */
public final class World {

    private final WorldConfig config;
    private final Random random;
    private final Board board;
    private final List<Npc> npcs = new ArrayList<>();
    private final List<WorldListener> listeners = new ArrayList<>();
    private final List<Integer> retired = new ArrayList<>();
    private int nextNpcId;
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
        for (int slot = 0; slot < config.npcs(); slot++) {
            Npc npc = new Npc(nextNpcId++);
            respawn(npc);
            if (config.agingEnabled()) {
                npc.lifespan = drawLifespan();
                // Founders start at evenly spread ages, so they die one after another, not all at once.
                npc.totalAge = (long) slot * config.lifespanTicks() / config.npcs();
            }
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
                    Optional.ofNullable(npc.finalObservation), neighboursOf(npc)));
        }
        return views;
    }

    /** Applies one action per NPC and advances the clock by one tick. */
    public void step(Map<Integer, Action> actions) {
        boolean ageing = config.agingEnabled() && tick >= config.agingStartTick();
        SeasonConfig seasons = config.seasons();
        Season season = seasons.seasonAt(tick);
        double[] consumeRewards = new double[npcs.size()];
        for (int i = 0; i < npcs.size(); i++) {
            Npc npc = npcs.get(i);
            Action action = actions.get(npc.id);
            if (action == null) {
                throw new IllegalArgumentException("No action for NPC " + npc.id + " at tick " + tick);
            }
            consumeRewards[i] = act(npc, action);
        }

        // Everyone has moved: now it is clear who stands in a shelter.
        Set<Npc> cold = season == Season.WINTER ? outInTheCold() : Set.of();
        for (Npc npc : npcs) {
            growNeeds(npc);
            drainEnergy(npc, cold.contains(npc));
            npc.age++;
            if (ageing || !config.agingEnabled()) {
                npc.totalAge++;
            }
        }

        tick++;
        board.grow(seasons.growthAt(tick - 1), config.economy().fieldYield());
        Season next = seasons.seasonAt(tick);
        boolean seasonChanged = next != season;
        if (seasonChanged && next == Season.WINTER && seasons.winterRot()) {
            board.winterRot(config.foodRegrowTicks());
        }
        double springBonus = seasonChanged && next == Season.SPRING ? config.rewards().winterSurvived() : 0;

        for (int i = 0; i < npcs.size(); i++) {
            settle(npcs.get(i), consumeRewards[i] + springBonus);
        }

        if (seasonChanged) {
            SeasonChanged event = new SeasonChanged(tick, next, tick / (4L * seasons.seasonTicks()));
            listeners.forEach(listener -> listener.onSeasonChanged(event));
        }

        if (config.agingEnabled()) {
            for (int slot = 0; slot < npcs.size(); slot++) {
                if (npcs.get(slot).totalAge >= npcs.get(slot).lifespan) {
                    replaceWithNewborn(slot);
                }
            }
        }
    }

    /**
     * Ids of the NPCs that died of old age since the previous call. The simulation passes them
     * to the brain, which then drops their memory.
     */
    public List<Integer> drainRetired() {
        List<Integer> ids = List.copyOf(retired);
        retired.clear();
        return ids;
    }

    public WorldSnapshot snapshot() {
        return new WorldSnapshot(tick, config.seasons().seasonAt(tick), board.width(), board.height(), board.cells(),
                board.amounts(), npcs.stream().map(Npc::snapshot).toList());
    }

    /** For tests: direct access to the NPC in a slot (slot = id until someone dies of old age). */
    Npc npc(int slot) {
        return npcs.get(slot);
    }

    /** Returns the reward earned by eating or drinking, zero for any other action. */
    private double act(Npc npc, Action action) {
        return switch (action) {
            case STAY, UP, DOWN, LEFT, RIGHT -> {
                move(npc, action);
                yield 0;
            }
            case CONSUME -> consume(npc);
            case GATHER -> {
                gather(npc);
                yield 0;
            }
            case EAT_CARRIED -> eatCarried(npc);
            case PLANT -> {
                plant(npc);
                yield 0;
            }
            case BUILD_SHELTER, BUILD_STORE, DEPOSIT, WITHDRAW -> 0;
        };
    }

    private void move(Npc npc, Action action) {
        Position target = npc.position.moved(action);
        if (board.get(target) != CellType.WALL) {
            npc.position = target;
        }
    }

    /** Eats wild food or drinks water on the NPC's own cell. */
    private double consume(Npc npc) {
        CellType cell = board.get(npc.position);
        if (cell == CellType.FOOD) {
            double reward = eat(npc);
            board.eatFood(npc.position, config.foodRegrowTicks());
            return reward;
        }
        if (cell == CellType.WATER) {
            RewardConfig rewards = config.rewards();
            double reward = npc.thirst >= rewards.consumeNeedMin() ? rewards.consume() : 0;
            npc.thirst = Math.max(0, npc.thirst - config.consumeRelief());
            return reward;
        }
        return 0;
    }

    private double eat(Npc npc) {
        RewardConfig rewards = config.rewards();
        double reward = npc.hunger >= rewards.consumeNeedMin() ? rewards.consume() : 0;
        npc.hunger = Math.max(0, npc.hunger - config.consumeRelief());
        return reward;
    }

    private double eatCarried(Npc npc) {
        if (npc.food == 0) {
            return 0;
        }
        npc.food--;
        return eat(npc);
    }

    /** Picks up one item from the NPC's own cell: food from wild food or a ripe field, wood from a tree. */
    private void gather(Npc npc) {
        EconomyConfig economy = config.economy();
        if (npc.food + npc.wood >= economy.inventorySize()) {
            return;
        }
        switch (board.get(npc.position)) {
            case FOOD -> {
                npc.food++;
                board.eatFood(npc.position, config.foodRegrowTicks());
            }
            case TREE -> {
                npc.wood++;
                board.startGrowth(npc.position, CellType.STUMP, CellType.TREE, economy.treeRegrowTicks());
            }
            case FIELD_RIPE -> {
                npc.food++;
                int left = board.amount(npc.position) - 1;
                board.setAmount(npc.position, left);
                if (left == 0) {
                    board.replace(npc.position, CellType.EMPTY);
                    FieldHarvested event = new FieldHarvested(tick + 1, npc.id, npc.position);
                    listeners.forEach(listener -> listener.onFieldHarvested(event));
                }
            }
            default -> {
            }
        }
    }

    /** Sows one carried portion on an empty cell. Wild food that was about to grow back there is gone. */
    private void plant(Npc npc) {
        if (npc.food == 0 || board.get(npc.position) != CellType.EMPTY) {
            return;
        }
        npc.food--;
        board.startGrowth(npc.position, CellType.FIELD_SOWN, CellType.FIELD_RIPE, config.economy().fieldGrowTicks());
        FieldSown event = new FieldSown(tick + 1, npc.id, npc.position);
        listeners.forEach(listener -> listener.onFieldSown(event));
    }

    private void growNeeds(Npc npc) {
        npc.hunger = Math.min(config.needMax(), npc.hunger + config.needPerTick());
        npc.thirst = Math.min(config.needMax(), npc.thirst + config.needPerTick());
    }

    /** In winter: the NPCs not protected by a shelter. A shelter protects the first NPCs on it, by slot. */
    private Set<Npc> outInTheCold() {
        Set<Npc> cold = new HashSet<>();
        Map<Position, Integer> places = new HashMap<>();
        for (Npc npc : npcs) {
            boolean protectedHere = board.get(npc.position) == CellType.SHELTER
                    && places.merge(npc.position, 1, Integer::sum) <= config.economy().shelterCapacity();
            if (!protectedHere) {
                cold.add(npc);
            }
        }
        return cold;
    }

    private void drainEnergy(Npc npc, boolean cold) {
        int loss = 0;
        if (cold) {
            loss += config.seasons().coldEnergyLoss();
        }
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
        if (config.lifeLimitEnabled() && npc.age >= config.lifeLimit()) {
            npc.truncated = true;
            npc.finalObservation = observe(npc);
            endLife(npc, LifeEnded.Cause.LIFE_LIMIT);
        }
    }

    private void endLife(Npc npc, LifeEnded.Cause cause) {
        LifeEnded event = new LifeEnded(npc.id, tick, npc.age, cause, npc.generation, npc.totalAge);
        respawn(npc);
        listeners.forEach(listener -> listener.onLifeEnded(event));
    }

    /** Old age: the NPC in the slot is gone and a newborn with a new id appears next to a living NPC. */
    private void replaceWithNewborn(int slot) {
        Npc old = npcs.get(slot);
        Npc newborn = new Npc(nextNpcId++);
        newborn.generation = old.generation + 1;
        newborn.lifespan = drawLifespan();
        newborn.hunger = 0;
        newborn.thirst = 0;
        newborn.energy = config.energyMax();

        Integer parentId = null;
        if (npcs.size() > 1) {
            int parentSlot = random.nextInt(npcs.size() - 1);
            if (parentSlot >= slot) {
                parentSlot++;
            }
            Npc parent = npcs.get(parentSlot);
            parentId = parent.id;
            newborn.position = safeCellAround(parent.position);
        }
        if (newborn.position == null) {
            respawn(newborn);
        }

        npcs.set(slot, newborn);
        retired.add(old.id);
        NpcRetired retiredEvent = new NpcRetired(old.id, tick, old.totalAge, old.generation);
        NpcBorn bornEvent = new NpcBorn(newborn.id, tick, newborn.generation, Optional.ofNullable(parentId));
        listeners.forEach(listener -> {
            listener.onNpcRetired(retiredEvent);
            listener.onNpcBorn(bornEvent);
        });
    }

    /** A random safe cell in the 3x3 around a position, or null when there is none. */
    private Position safeCellAround(Position centre) {
        List<Position> safe = new ArrayList<>(9);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (board.isSafe(centre.x() + dx, centre.y() + dy)) {
                    safe.add(new Position(centre.x() + dx, centre.y() + dy));
                }
            }
        }
        return safe.isEmpty() ? null : safe.get(random.nextInt(safe.size()));
    }

    private long drawLifespan() {
        double spread = config.lifespanSpread();
        double factor = 1 + spread * (2 * random.nextDouble() - 1);
        return Math.max(1, Math.round(config.lifespanTicks() * factor));
    }

    private Observation observe(Npc npc) {
        int radius = config.viewRadius();
        List<CellType> surroundings = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = npc.position.x() + dx;
                int y = npc.position.y() + dy;
                // Beyond the board there is only more wall.
                surroundings.add(board.contains(x, y) ? board.get(x, y) : CellType.WALL);
            }
        }
        Season season = config.seasons().seasonAt(tick);
        return new Observation(surroundings, npc.hunger, npc.thirst, npc.energy, npc.position, npc.food, npc.wood,
                season == null ? 0 : season.ordinal() + 1, config.seasons().ticksLeftAt(tick), board.amount(npc.position));
    }

    /** Ids of the other NPCs standing in the 3x3 around the NPC, in slot order. */
    private List<Integer> neighboursOf(Npc npc) {
        List<Integer> neighbours = new ArrayList<>();
        for (Npc other : npcs) {
            if (other != npc
                    && Math.abs(other.position.x() - npc.position.x()) <= 1
                    && Math.abs(other.position.y() - npc.position.y()) <= 1) {
                neighbours.add(other.id);
            }
        }
        return neighbours;
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
