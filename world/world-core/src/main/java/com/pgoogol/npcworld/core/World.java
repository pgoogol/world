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
    // Stage 3: an NPC that died for good shows up once more, dead, so its brain gets the penalty;
    // its id reaches the brain as retired one tick later.
    private final List<NpcView> departed = new ArrayList<>();
    private final List<Integer> retiringNext = new ArrayList<>();
    private int nextNpcId;
    private long tick;
    private long lastBirthTick = Long.MIN_VALUE / 4;

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
        int radius = config.population().foundersRadius();
        Position camp = radius > 0 ? randomSafeCell() : null;
        for (int slot = 0; slot < config.npcs(); slot++) {
            Npc npc = new Npc(nextNpcId++);
            if (camp == null) {
                respawn(npc);
            } else {
                settleNear(npc, camp, radius);
            }
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

    /** Living NPCs. */
    public int population() {
        return npcs.size();
    }

    /**
     * What every NPC sees right now, together with the outcome of its previous action. In stage 3
     * an NPC that has just died for good comes last, once, with {@code died} set; its action is ignored.
     */
    public List<NpcView> views() {
        List<NpcView> views = new ArrayList<>(npcs.size() + departed.size());
        for (Npc npc : npcs) {
            views.add(view(npc));
        }
        views.addAll(departed);
        return views;
    }

    private NpcView view(Npc npc) {
        return new NpcView(npc.id, observe(npc), npc.reward, npc.died, npc.truncated,
                Optional.ofNullable(npc.finalObservation), neighboursOf(npc));
    }

    /** Applies one action per NPC and advances the clock by one tick. */
    public void step(Map<Integer, Action> actions) {
        boolean ageing = config.agingEnabled() && tick >= config.agingStartTick();
        departed.clear();
        retired.addAll(retiringNext);
        retiringNext.clear();
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

        List<Npc> dead = new ArrayList<>();
        for (int i = 0; i < npcs.size(); i++) {
            if (settle(npcs.get(i), consumeRewards[i] + springBonus)) {
                dead.add(npcs.get(i));
            }
        }
        for (Npc npc : dead) {
            // Death is final: the NPC leaves the world after one last view.
            departed.add(view(npc));
            npcs.remove(npc);
            retiringNext.add(npc.id);
        }

        if (seasonChanged) {
            SeasonChanged event = new SeasonChanged(tick, next, tick / (4L * seasons.seasonTicks()));
            listeners.forEach(listener -> listener.onSeasonChanged(event));
        }

        if (config.agingEnabled() && config.population().newbornOnOldAge()) {
            // Stage 2. Slots in ascending order: the order decides the random draws.
            for (int slot = 0; slot < npcs.size(); slot++) {
                if (npcs.get(slot).totalAge >= npcs.get(slot).lifespan) {
                    replaceWithNewborn(slot);
                }
            }
        } else if (config.agingEnabled()) {
            for (int slot = npcs.size() - 1; slot >= 0; slot--) {
                if (npcs.get(slot).totalAge >= npcs.get(slot).lifespan) {
                    dieOfOldAge(slot);
                }
            }
        }
        if (config.population().birthsEnabled()) {
            maybeBirth();
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

    /** For tests: direct access to the NPC in a slot (slot = id until someone dies or is born). */
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
            case BUILD_SHELTER -> {
                build(npc, CellType.SITE_SHELTER, CellType.SHELTER);
                yield 0;
            }
            case BUILD_STORE -> {
                build(npc, CellType.SITE_STORE, CellType.STORE);
                yield 0;
            }
            case DEPOSIT -> {
                deposit(npc);
                yield 0;
            }
            case WITHDRAW -> {
                withdraw(npc);
                yield 0;
            }
        };
    }

    private void move(Npc npc, Action action) {
        Position target = npc.position.moved(action);
        if (board.get(target) != CellType.WALL) {
            npc.position = target;
        }
    }

    /**
     * Eats or drinks on the NPC's own cell: wild food, water and, in stage 3, one portion straight
     * from a ripe field or a store.
     */
    private double consume(Npc npc) {
        CellType cell = board.get(npc.position);
        if (cell == CellType.FOOD) {
            double reward = eat(npc);
            board.eatFood(npc.position, config.foodRegrowTicks());
            return reward;
        }
        if (cell == CellType.FIELD_RIPE) {
            double reward = eat(npc);
            takePortionFromField(npc);
            return reward;
        }
        if (cell == CellType.STORE && board.amount(npc.position) > 0) {
            board.setAmount(npc.position, board.amount(npc.position) - 1);
            return eat(npc);
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
                takePortionFromField(npc);
            }
            default -> {
            }
        }
    }

    /** One portion less on the ripe field under the NPC; the last one leaves an empty cell. */
    private void takePortionFromField(Npc npc) {
        int left = board.amount(npc.position) - 1;
        board.setAmount(npc.position, left);
        if (left == 0) {
            board.replace(npc.position, CellType.EMPTY);
            FieldHarvested event = new FieldHarvested(tick + 1, npc.id, npc.position);
            listeners.forEach(listener -> listener.onFieldHarvested(event));
        }
    }

    /**
     * One step of building: on an empty cell it starts a site, on a site of the same kind it brings one
     * wood or, once the site has all its wood, adds one tick of work. The last tick of work finishes it.
     */
    private void build(Npc npc, CellType site, CellType building) {
        EconomyConfig economy = config.economy();
        if (economy.inventorySize() == 0) {
            return;
        }
        CellType cell = board.get(npc.position);
        if (cell == CellType.EMPTY) {
            board.replace(npc.position, site);
            board.setAmount(npc.position, 0);
            board.setWork(npc.position, 0);
            return;
        }
        if (cell != site) {
            return;
        }
        boolean shelter = building == CellType.SHELTER;
        int woodNeeded = shelter ? economy.shelterWood() : economy.storeWood();
        int workNeeded = shelter ? economy.shelterWork() : economy.storeWork();
        int wood = board.amount(npc.position);
        if (wood < woodNeeded) {
            if (npc.wood > 0) {
                npc.wood--;
                board.setAmount(npc.position, wood + 1);
            }
            return;
        }
        int work = board.work(npc.position) + 1;
        board.setWork(npc.position, work);
        if (work >= workNeeded) {
            board.replace(npc.position, building);
            board.setAmount(npc.position, 0);
            board.setWork(npc.position, 0);
            BuildingCompleted event = new BuildingCompleted(tick + 1, npc.id, npc.position, building);
            listeners.forEach(listener -> listener.onBuildingCompleted(event));
        }
    }

    /** Puts all carried food into the store under the NPC, as much as fits. */
    private void deposit(Npc npc) {
        if (board.get(npc.position) != CellType.STORE) {
            return;
        }
        int stored = board.amount(npc.position);
        int moved = Math.min(npc.food, config.economy().storeCapacity() - stored);
        npc.food -= moved;
        board.setAmount(npc.position, stored + moved);
    }

    /** Takes one portion from the store under the NPC, if there is one and the NPC has room. */
    private void withdraw(Npc npc) {
        int stored = board.amount(npc.position);
        if (board.get(npc.position) != CellType.STORE || stored == 0
                || npc.food + npc.wood >= config.economy().inventorySize()) {
            return;
        }
        board.setAmount(npc.position, stored - 1);
        npc.food++;
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

    /** Sets the reward for the tick. Returns true when the NPC died for good (stage 3). */
    private boolean settle(Npc npc, double consumeReward) {
        npc.died = false;
        npc.truncated = false;
        npc.finalObservation = null;

        if (npc.energy <= 0) {
            npc.reward = config.rewards().death();
            npc.died = true;
            if (config.population().deathIsFinal()) {
                LifeEnded event = new LifeEnded(npc.id, tick, npc.age, LifeEnded.Cause.DEATH, npc.generation, npc.totalAge);
                listeners.forEach(listener -> listener.onLifeEnded(event));
                return true;
            }
            endLife(npc, LifeEnded.Cause.DEATH);
            return false;
        }

        npc.reward = config.rewards().perTick() + consumeReward;
        if (config.lifeLimitEnabled() && npc.age >= config.lifeLimit()) {
            npc.truncated = true;
            npc.finalObservation = observe(npc);
            endLife(npc, LifeEnded.Cause.LIFE_LIMIT);
        }
        return false;
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

    /** Stage 3: old age without a replacement. Births come from the stores instead. */
    private void dieOfOldAge(int slot) {
        Npc old = npcs.remove(slot);
        retired.add(old.id);
        NpcRetired event = new NpcRetired(old.id, tick, old.totalAge, old.generation);
        listeners.forEach(listener -> listener.onNpcRetired(event));
    }

    /**
     * Stage 3: a newborn appears next to the fullest store when the stores hold the birth cost on top of
     * a reserve for every living NPC, the shelters have a free place and enough time passed since the
     * last birth. The cost comes out of the fullest store.
     */
    private void maybeBirth() {
        PopulationConfig population = config.population();
        int living = npcs.size();
        if (living < 2 || living >= population.maxNpcs() || tick - lastBirthTick < population.birthIntervalTicks()) {
            return;
        }
        if (living >= board.count(CellType.SHELTER) * config.economy().shelterCapacity()) {
            return;
        }
        Position fullest = null;
        int stored = 0;
        for (Position store : board.positionsOf(CellType.STORE)) {
            stored += board.amount(store);
            if (fullest == null || board.amount(store) > board.amount(fullest)) {
                fullest = store;
            }
        }
        if (fullest == null || stored < population.birthFoodCost() + population.birthReservePerNpc() * living
                || board.amount(fullest) < population.birthFoodCost()) {
            return;
        }
        board.setAmount(fullest, board.amount(fullest) - population.birthFoodCost());

        Npc parent = npcs.get(random.nextInt(living));
        Npc newborn = new Npc(nextNpcId++);
        newborn.generation = parent.generation + 1;
        newborn.energy = config.energyMax();
        if (config.agingEnabled()) {
            newborn.lifespan = drawLifespan();
        }
        newborn.position = safeCellAround(fullest);
        if (newborn.position == null) {
            respawn(newborn);
        }
        npcs.add(newborn);
        lastBirthTick = tick;
        NpcBorn event = new NpcBorn(newborn.id, tick, newborn.generation, Optional.of(parent.id));
        listeners.forEach(listener -> listener.onNpcBorn(event));
    }

    private Position randomSafeCell() {
        int x;
        int y;
        do {
            x = 1 + random.nextInt(config.width() - 2);
            y = 1 + random.nextInt(config.height() - 2);
        } while (!board.isSafe(x, y));
        return new Position(x, y);
    }

    /** A founder: a random safe cell at most {@code radius} cells from the camp in each direction. */
    private void settleNear(Npc npc, Position camp, int radius) {
        int x;
        int y;
        do {
            x = camp.x() - radius + random.nextInt(2 * radius + 1);
            y = camp.y() - radius + random.nextInt(2 * radius + 1);
        } while (!board.contains(x, y) || !board.isSafe(x, y));
        npc.position = new Position(x, y);
        npc.hunger = 0;
        npc.thirst = 0;
        npc.energy = config.energyMax();
        npc.age = 0;
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
