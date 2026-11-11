"""The group of brains behind one Brain.Step call."""

from collections.abc import Callable
from pathlib import Path

from brain.brains import ACTIONS
from brain.proto import brain_pb2
from brain.storage import save_brains


class BrainGroup:
    """Keeps one brain per NPC id, created on the first request that mentions the NPC.

    With `save_path` set, the group saves what the brains have learned every `save_every_ticks` ticks.
    """

    def __init__(
        self,
        brain_factory: Callable[[int], object],
        save_path: Path | None = None,
        save_every_ticks: int = 0,
    ):
        self._brain_factory = brain_factory
        self._save_path = save_path
        self._save_every_ticks = save_every_ticks
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

        if self._save_every_ticks > 0 and request.tick > 0 and request.tick % self._save_every_ticks == 0:
            self.save()
        return response

    def save(self) -> None:
        if self._save_path is not None:
            save_brains(self._save_path, self.brains)
