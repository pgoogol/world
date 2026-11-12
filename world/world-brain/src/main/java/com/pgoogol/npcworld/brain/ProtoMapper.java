package com.pgoogol.npcworld.brain;

import com.pgoogol.npcworld.brain.proto.Cell;
import com.pgoogol.npcworld.brain.proto.StepRequest;
import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.NpcView;
import com.pgoogol.npcworld.core.Observation;
import java.util.List;

/** Translates between the world's own types and the generated contract classes. */
final class ProtoMapper {

    private ProtoMapper() {
    }

    static StepRequest toRequest(long tick, List<NpcView> views, List<Integer> retiredNpcIds) {
        StepRequest.Builder request = StepRequest.newBuilder().setTick(tick).addAllRetiredNpcIds(retiredNpcIds);
        for (NpcView view : views) {
            var npc = com.pgoogol.npcworld.brain.proto.NpcView.newBuilder()
                    .setNpcId(view.npcId())
                    .setObservation(toProto(view.observation()))
                    .setReward(view.reward())
                    .setDied(view.died())
                    .setTruncated(view.truncated())
                    .addAllNeighbourIds(view.neighbours());
            view.finalObservation().ifPresent(observation -> npc.setFinalObservation(toProto(observation)));
            request.addNpcs(npc);
        }
        return request.build();
    }

    static com.pgoogol.npcworld.brain.proto.Observation toProto(Observation observation) {
        var builder = com.pgoogol.npcworld.brain.proto.Observation.newBuilder()
                .setHunger(observation.hunger())
                .setThirst(observation.thirst())
                .setEnergy(observation.energy())
                .setX(observation.position().x())
                .setY(observation.position().y())
                .setViewSize(observation.viewSize())
                .setCarriedFood(observation.carriedFood())
                .setCarriedWood(observation.carriedWood())
                .setSeason(observation.season())
                .setSeasonTicksLeft(observation.seasonTicksLeft())
                .setHereAmount(observation.hereAmount());
        observation.surroundings().forEach(cell -> builder.addSurroundings(toProto(cell)));
        return builder.build();
    }

    static Cell toProto(CellType cell) {
        return switch (cell) {
            case EMPTY -> Cell.CELL_EMPTY;
            case FOOD -> Cell.CELL_FOOD;
            case WATER -> Cell.CELL_WATER;
            case LAVA -> Cell.CELL_LAVA;
            case WALL -> Cell.CELL_WALL;
            case TREE -> Cell.CELL_TREE;
            case STUMP -> Cell.CELL_STUMP;
            case FIELD_SOWN -> Cell.CELL_FIELD_SOWN;
            case FIELD_RIPE -> Cell.CELL_FIELD_RIPE;
            case SITE_SHELTER -> Cell.CELL_SITE_SHELTER;
            case SITE_STORE -> Cell.CELL_SITE_STORE;
            case SHELTER -> Cell.CELL_SHELTER;
            case STORE -> Cell.CELL_STORE;
        };
    }

    static Action fromProto(int npcId, com.pgoogol.npcworld.brain.proto.Action action) {
        return switch (action) {
            case ACTION_STAY -> Action.STAY;
            case ACTION_UP -> Action.UP;
            case ACTION_DOWN -> Action.DOWN;
            case ACTION_LEFT -> Action.LEFT;
            case ACTION_RIGHT -> Action.RIGHT;
            case ACTION_CONSUME -> Action.CONSUME;
            case ACTION_GATHER -> Action.GATHER;
            case ACTION_EAT_CARRIED -> Action.EAT_CARRIED;
            case ACTION_PLANT -> Action.PLANT;
            case ACTION_BUILD_SHELTER -> Action.BUILD_SHELTER;
            case ACTION_BUILD_STORE -> Action.BUILD_STORE;
            case ACTION_DEPOSIT -> Action.DEPOSIT;
            case ACTION_WITHDRAW -> Action.WITHDRAW;
            case ACTION_UNSPECIFIED, UNRECOGNIZED ->
                    throw new BrainProtocolException("Brain sent no valid action for NPC " + npcId + ": " + action);
        };
    }
}
