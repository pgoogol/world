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

    static StepRequest toRequest(long tick, List<NpcView> views) {
        StepRequest.Builder request = StepRequest.newBuilder().setTick(tick);
        for (NpcView view : views) {
            var npc = com.pgoogol.npcworld.brain.proto.NpcView.newBuilder()
                    .setNpcId(view.npcId())
                    .setObservation(toProto(view.observation()))
                    .setReward(view.reward())
                    .setDied(view.died())
                    .setTruncated(view.truncated());
            view.finalObservation().ifPresent(observation -> npc.setFinalObservation(toProto(observation)));
            request.addNpcs(npc);
        }
        return request.build();
    }

    static com.pgoogol.npcworld.brain.proto.Observation toProto(Observation observation) {
        var builder = com.pgoogol.npcworld.brain.proto.Observation.newBuilder()
                .setHunger(observation.hunger())
                .setThirst(observation.thirst())
                .setEnergy(observation.energy());
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
            case ACTION_UNSPECIFIED, UNRECOGNIZED ->
                    throw new BrainProtocolException("Brain sent no valid action for NPC " + npcId + ": " + action);
        };
    }
}
