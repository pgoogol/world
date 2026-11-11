"""The group of brains behind one Brain.Step call."""

from collections.abc import Callable

from brain.brains import ACTIONS
from brain.proto import brain_pb2


class BrainGroup:
    """Keeps one brain per NPC id, created on the first request that mentions the NPC."""

    def __init__(self, brain_factory: Callable[[int], object]):
        self._brain_factory = brain_factory
        self.brains: dict[int, object] = {}

    def step(self, request: brain_pb2.StepRequest) -> brain_pb2.StepResponse:
        if request.tick == 0:
            # The server outlives the world: tick 0 means a new run started.
            for brain in self.brains.values():
                brain.start_run()

        response = brain_pb2.StepResponse()
        for view in request.npcs:
            brain = self.brains.get(view.npc_id)
            if brain is None:
                brain = self.brains[view.npc_id] = self._brain_factory(view.npc_id)
            response.actions.add(npc_id=view.npc_id, action=ACTIONS[brain.act(view)])
        return response
