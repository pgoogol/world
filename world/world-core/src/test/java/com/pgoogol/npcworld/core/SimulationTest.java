package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationTest {

    @Test
    void callsTheBrainOncePerTickForTheWholeGroup() {
        List<Long> ticks = new ArrayList<>();
        List<Integer> groupSizes = new ArrayList<>();
        Brain brain = (tick, views) -> {
            ticks.add(tick);
            groupSizes.add(views.size());
            return allDo(views, Action.STAY);
        };
        Simulation simulation = new Simulation(new World(TestConfigs.emptyBoard().build()), brain);

        simulation.run(3);

        assertThat(ticks).containsExactly(0L, 1L, 2L);
        assertThat(groupSizes).containsOnly(5);
        assertThat(simulation.world().tick()).isEqualTo(3);
    }

    @Test
    void viewHoldsNineCellsWithTheNpcInTheCentre() {
        World world = new World(TestConfigs.emptyBoard().size(3).npcs(1).build());

        Observation observation = world.views().getFirst().observation();

        assertThat(observation.surroundings()).hasSize(9);
        assertThat(observation.surroundings().get(4)).isEqualTo(CellType.EMPTY);
        assertThat(observation.surroundings()).filteredOn(cell -> cell == CellType.WALL).hasSize(8);
    }

    @Test
    void missingActionIsAnError() {
        World world = new World(TestConfigs.emptyBoard().build());

        assertThatThrownBy(() -> world.step(Map.of(0, Action.STAY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No action for NPC 1");
    }

    static Map<Integer, Action> allDo(List<NpcView> views, Action action) {
        Map<Integer, Action> actions = new HashMap<>();
        views.forEach(view -> actions.put(view.npcId(), action));
        return actions;
    }
}
