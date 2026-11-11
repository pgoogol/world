package com.pgoogol.npcworld.brain;

import com.pgoogol.npcworld.brain.proto.BrainGrpc;
import com.pgoogol.npcworld.brain.proto.NpcAction;
import com.pgoogol.npcworld.brain.proto.StepResponse;
import com.pgoogol.npcworld.core.Action;
import com.pgoogol.npcworld.core.Brain;
import com.pgoogol.npcworld.core.NpcView;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Asks the brains server for actions: one blocking {@code Step} call per tick. */
public final class GrpcBrain implements Brain, AutoCloseable {

    private final ManagedChannel channel;
    private final BrainGrpc.BrainBlockingStub stub;
    private final List<Integer> retiredNpcIds = new ArrayList<>();

    public GrpcBrain(ManagedChannel channel) {
        this.channel = channel;
        this.stub = BrainGrpc.newBlockingStub(channel);
    }

    /** Connects without TLS: both processes run on the same machine. */
    public static GrpcBrain connect(String address) {
        return new GrpcBrain(Grpc.newChannelBuilder(address, InsecureChannelCredentials.create()).build());
    }

    @Override
    public Map<Integer, Action> decide(long tick, List<NpcView> views) {
        StepResponse response = stub.step(ProtoMapper.toRequest(tick, views, retiredNpcIds));
        retiredNpcIds.clear();

        Map<Integer, Action> actions = new HashMap<>();
        for (NpcAction npcAction : response.getActionsList()) {
            Action action = ProtoMapper.fromProto(npcAction.getNpcId(), npcAction.getAction());
            if (actions.put(npcAction.getNpcId(), action) != null) {
                throw new BrainProtocolException("Brain sent two actions for NPC " + npcAction.getNpcId());
            }
        }
        for (NpcView view : views) {
            if (!actions.containsKey(view.npcId())) {
                throw new BrainProtocolException("Brain sent no action for NPC " + view.npcId());
            }
        }
        if (actions.size() != views.size()) {
            throw new BrainProtocolException("Brain sent actions for NPCs that were not in the request");
        }
        return actions;
    }

    /** Sent with the next {@code Step}, so the tick still costs a single call. */
    @Override
    public void npcsRetired(List<Integer> npcIds) {
        retiredNpcIds.addAll(npcIds);
    }

    @Override
    public void close() throws InterruptedException {
        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow();
        }
    }
}
