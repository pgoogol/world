"""The group of brains behind one Brain.Step call."""

from collections.abc import Callable
from pathlib import Path

from brain.brains import ACTIONS, QLearningBrain
from brain.config import SharingConfig
from brain.proto import brain_pb2
from brain.storage import save_brains


class BrainGroup:
    """Keeps one brain per living NPC.

    A brain is created on the first request that mentions the NPC and dropped when the world
    reports that the NPC died of old age. With `sharing` set, knowledge passes between
    neighbours: newborns inherit, NPCs learn from each other's steps and swap discovery maps.

    With `save_path` set, the group saves what the brains have learned every `save_every_ticks` ticks.
    """

    def __init__(
        self,
        brain_factory: Callable[[int], object],
        save_path: Path | None = None,
        save_every_ticks: int = 0,
        sharing: SharingConfig | None = None,
    ):
        self._brain_factory = brain_factory
        self._save_path = save_path
        self._save_every_ticks = save_every_ticks
        self._sharing = sharing or SharingConfig()
        self.brains: dict[int, object] = {}

    def step(self, request: brain_pb2.StepRequest) -> brain_pb2.StepResponse:
        for npc_id in request.retired_npc_ids:
            # Death of old age: the memory goes with the NPC.
            self.brains.pop(npc_id, None)

        if request.tick == 0:
            # The server outlives the world: tick 0 means a new run started.
            for brain in self.brains.values():
                brain.start_run()

        self._welcome_newborns(request)

        response = brain_pb2.StepResponse()
        for view in request.npcs:
            action = self.brains[view.npc_id].act(view, request.tick)
            response.actions.add(npc_id=view.npc_id, action=ACTIONS[action])

        if self._sharing.observe:
            self._learn_from_neighbours(request)
        if self._sharing.map:
            self._swap_maps(request)

        if self._save_every_ticks > 0 and request.tick > 0 and request.tick % self._save_every_ticks == 0:
            self.save()
        return response

    def save(self) -> None:
        if self._save_path is not None:
            save_brains(self._save_path, self.brains)

    def _welcome_newborns(self, request: brain_pb2.StepRequest) -> None:
        """Creates brains for NPCs seen for the first time; with inheritance they start from their neighbours."""
        newborns = [view for view in request.npcs if view.npc_id not in self.brains]
        elders_by_newborn = {
            view.npc_id: [self.brains[other] for other in view.neighbour_ids if other in self.brains]
            for view in newborns
        }
        for view in newborns:
            brain = self.brains[view.npc_id] = self._brain_factory(view.npc_id)
            elders = [elder for elder in elders_by_newborn[view.npc_id] if isinstance(elder, QLearningBrain)]
            if self._sharing.inherit and elders and isinstance(brain, QLearningBrain):
                brain.inherit(elders, self._sharing.inherit_trust)

    def _learn_from_neighbours(self, request: brain_pb2.StepRequest) -> None:
        for view in request.npcs:
            observer = self.brains[view.npc_id]
            if not isinstance(observer, QLearningBrain):
                continue
            for neighbour_id in view.neighbour_ids:
                neighbour = self.brains.get(neighbour_id)
                transition = getattr(neighbour, "last_transition", None)
                if transition is not None:
                    observer.learn_from(transition, self._sharing.observe_weight)

    def _swap_maps(self, request: brain_pb2.StepRequest) -> None:
        for view in request.npcs:
            mine = getattr(self.brains[view.npc_id], "discovery", None)
            if mine is None:
                continue
            for neighbour_id in view.neighbour_ids:
                theirs = getattr(self.brains.get(neighbour_id), "discovery", None)
                # Each pair shows up twice (once per side); merge once, in both directions.
                if theirs is not None and view.npc_id < neighbour_id:
                    mine.merge(theirs)
                    theirs.merge(mine)
