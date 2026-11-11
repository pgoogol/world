package com.pgoogol.npcworld.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pgoogol.npcworld.brain.proto.BrainGrpc;
import com.pgoogol.npcworld.brain.proto.Cell;
import com.pgoogol.npcworld.brain.proto.NpcAction;
import com.pgoogol.npcworld.brain.proto.StepRequest;
import com.pgoogol.npcworld.brain.proto.StepResponse;
import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.NpcView;
import com.pgoogol.npcworld.core.Observation;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcBrainTest {

    private Server server;
    private GrpcBrain brain;
    private StepRequest lastRequest;

    @AfterEach
    void stop() throws InterruptedException {
        brain.close();
        server.shutdownNow();
    }

    @Test
    void returnsTheActionChosenForEachNpc() throws Exception {
        startBrain(request -> StepResponse.newBuilder()
                .addActions(action(0, com.pgoogol.npcworld.brain.proto.Action.ACTION_LEFT))
                .addActions(action(1, com.pgoogol.npcworld.brain.proto.Action.ACTION_CONSUME))
                .build());

        Map<Integer, Action> actions = brain.decide(0, List.of(view(0), view(1)));

        assertThat(actions).containsOnly(Map.entry(0, Action.LEFT), Map.entry(1, Action.CONSUME));
    }

    @Test
    void sendsWhatTheNpcSeesAndTheOutcomeOfItsPreviousAction() throws Exception {
        startBrain(request -> stayFor(request));
        Observation current = new Observation(nineCells(CellType.FOOD), 55, 12, 90);
        Observation beforeReset = new Observation(nineCells(CellType.LAVA), 70, 81, 3);

        brain.decide(17, List.of(new NpcView(4, current, 1.1, false, true, Optional.of(beforeReset))));

        assertThat(lastRequest.getTick()).isEqualTo(17);
        var npc = lastRequest.getNpcs(0);
        assertThat(npc.getNpcId()).isEqualTo(4);
        assertThat(npc.getReward()).isEqualTo(1.1);
        assertThat(npc.getDied()).isFalse();
        assertThat(npc.getTruncated()).isTrue();
        assertThat(npc.getObservation().getSurroundingsList()).hasSize(9).containsOnly(Cell.CELL_FOOD);
        assertThat(npc.getObservation().getHunger()).isEqualTo(55);
        assertThat(npc.getObservation().getThirst()).isEqualTo(12);
        assertThat(npc.getObservation().getEnergy()).isEqualTo(90);
        assertThat(npc.getFinalObservation().getSurroundingsList()).containsOnly(Cell.CELL_LAVA);
        assertThat(npc.getFinalObservation().getThirst()).isEqualTo(81);
    }

    @Test
    void leavesFinalObservationUnsetWhenTheEpisodeGoesOn() throws Exception {
        startBrain(request -> stayFor(request));

        brain.decide(0, List.of(view(0)));

        assertThat(lastRequest.getNpcs(0).hasFinalObservation()).isFalse();
    }

    @Test
    void rejectsAnUnspecifiedAction() throws Exception {
        startBrain(request -> StepResponse.newBuilder()
                .addActions(NpcAction.newBuilder().setNpcId(0))
                .build());

        assertThatThrownBy(() -> brain.decide(0, List.of(view(0))))
                .isInstanceOf(BrainProtocolException.class)
                .hasMessageContaining("NPC 0");
    }

    @Test
    void rejectsAnAnswerThatSkipsAnNpc() throws Exception {
        startBrain(request -> StepResponse.newBuilder()
                .addActions(action(0, com.pgoogol.npcworld.brain.proto.Action.ACTION_STAY))
                .build());

        assertThatThrownBy(() -> brain.decide(0, List.of(view(0), view(1))))
                .isInstanceOf(BrainProtocolException.class)
                .hasMessageContaining("no action for NPC 1");
    }

    @Test
    void rejectsAnActionForAnNpcThatWasNotAsked() throws Exception {
        startBrain(request -> StepResponse.newBuilder()
                .addActions(action(0, com.pgoogol.npcworld.brain.proto.Action.ACTION_STAY))
                .addActions(action(9, com.pgoogol.npcworld.brain.proto.Action.ACTION_STAY))
                .build());

        assertThatThrownBy(() -> brain.decide(0, List.of(view(0))))
                .isInstanceOf(BrainProtocolException.class);
    }

    private void startBrain(Function<StepRequest, StepResponse> answer) throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new BrainGrpc.BrainImplBase() {
                    @Override
                    public void step(StepRequest request, StreamObserver<StepResponse> observer) {
                        lastRequest = request;
                        observer.onNext(answer.apply(request));
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
        brain = new GrpcBrain(InProcessChannelBuilder.forName(name).directExecutor().build());
    }

    private static StepResponse stayFor(StepRequest request) {
        StepResponse.Builder response = StepResponse.newBuilder();
        request.getNpcsList().forEach(npc ->
                response.addActions(action(npc.getNpcId(), com.pgoogol.npcworld.brain.proto.Action.ACTION_STAY)));
        return response.build();
    }

    private static NpcAction action(int npcId, com.pgoogol.npcworld.brain.proto.Action action) {
        return NpcAction.newBuilder().setNpcId(npcId).setAction(action).build();
    }

    private static NpcView view(int npcId) {
        return new NpcView(npcId, new Observation(nineCells(CellType.EMPTY), 0, 0, 100), 0, false, false,
                Optional.empty());
    }

    private static List<CellType> nineCells(CellType type) {
        return Collections.nCopies(9, type);
    }
}
