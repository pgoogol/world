package com.pgoogol.npcworld.brain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgoogol.npcworld.brain.proto.Action;
import com.pgoogol.npcworld.brain.proto.BrainGrpc;
import com.pgoogol.npcworld.brain.proto.Cell;
import com.pgoogol.npcworld.brain.proto.NpcView;
import com.pgoogol.npcworld.brain.proto.Observation;
import com.pgoogol.npcworld.brain.proto.StepRequest;
import org.junit.jupiter.api.Test;

class GeneratedContractTest {

    @Test
    void stepRequestSurvivesSerialization() throws Exception {
        Observation observation = Observation.newBuilder()
                .addSurroundings(Cell.CELL_LAVA)
                .setHunger(10)
                .setThirst(20)
                .setEnergy(100)
                .build();
        StepRequest request = StepRequest.newBuilder()
                .setTick(7)
                .addNpcs(NpcView.newBuilder().setNpcId(3).setObservation(observation).setReward(0.1))
                .build();

        StepRequest parsed = StepRequest.parseFrom(request.toByteArray());

        assertThat(parsed).isEqualTo(request);
        assertThat(parsed.getNpcs(0).hasFinalObservation()).isFalse();
    }

    @Test
    void unsetEnumReadsAsUnspecified() {
        assertThat(Action.forNumber(0)).isEqualTo(Action.ACTION_UNSPECIFIED);
        assertThat(Cell.forNumber(0)).isEqualTo(Cell.CELL_UNSPECIFIED);
    }

    @Test
    void brainServiceExposesStep() {
        assertThat(BrainGrpc.SERVICE_NAME).isEqualTo("npcworld.brain.v1.Brain");
        assertThat(BrainGrpc.getStepMethod().getFullMethodName()).isEqualTo("npcworld.brain.v1.Brain/Step");
    }
}
