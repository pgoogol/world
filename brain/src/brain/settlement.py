"""Stage 3: a settlement whose NPCs all decide with one network.

Every NPC adds its experience to the same network, so a newborn has the settlement's knowledge from
its first tick: the stage 3 counterpart of inheriting Q-tables. What stays personal is the place map,
which NPCs swap on contact as in stage 2.
"""

import copy
import dataclasses
import os
from pathlib import Path

import torch

from brain.brains import npc_rng
from brain.config import BrainConfig
from brain.places import PlaceMap, WINTER
from brain.ppo import Ppo, Step
from brain.proto import brain_pb2 as pb
from brain.skills import Member, RawActions, Situation, Skills, note_full_shelter

# Kinds of places the network gets as "known / direction / distance" inputs.
PLACE_INPUTS = ("water", "food", "tree", "store", "shelter", "site")
DISTANCE_SCALE = 32.0
SCALARS = 14 + 4 * len(PLACE_INPUTS) + 2

MOVES = frozenset((pb.ACTION_UP, pb.ACTION_DOWN, pb.ACTION_LEFT, pb.ACTION_RIGHT))

# The control settlement of the stage: no planting, storing and building.
NO_BUILDING = ("store_food", "take_food", "plant", "build_shelter", "build_store", "take_shelter")


def build_choices(config: BrainConfig, build: bool = True):
    network = config.network
    if network.choices == "actions":
        return RawActions(config.need_levels[0])
    if network.choices == "skills":
        enabled = None
        if not build:
            enabled = frozenset(i for i, name in enumerate(Skills.names) if name not in NO_BUILDING)
        return Skills(network.inventory_size, (network.board_width, network.board_height), enabled)
    raise ValueError(f"Unknown brain.network.choices: {network.choices!r}")


class NetworkSettlement:
    """Answers Brain.Step for the whole settlement and, when learning, trains the network with PPO."""

    def __init__(self, config: BrainConfig, learning: bool = True, save_path: Path | None = None,
                 build: bool = True, load: bool = False):
        self._config = config
        self._network = config.network
        self._learning = learning
        self._save_path = save_path
        self.choices = build_choices(config, build)
        # The one skill whose work is standing still: sitting out the winter in a shelter.
        names = self.choices.names
        self._stay_skill = names.index("take_shelter") if "take_shelter" in names else None
        self.ppo = Ppo(self._network.view_size ** 2, SCALARS, self.choices.count, self._network, config.seed)
        # Ticks this network has learned from, over all runs; they shorten the warm-up.
        self.learned_ticks = 0
        if load and save_path is not None and save_path.exists():
            state = torch.load(save_path, weights_only=False)
            self.ppo.load_state(state)
            self.learned_ticks = state.get("learned_ticks", 0)
        # Warm-up (step 3.11): in the first ticks of every world the scenario of step 3.10 leads the NPCs
        # and nothing is learned, so the network starts in a settlement that already stands.
        self._guide = None
        self.warmup = 0
        if learning and self._network.warmup_ticks > 0:
            if config.scenario is None:
                raise ValueError("brain.network.warmup_ticks needs a brain.scripted section in the config")
            from brain.skill_script import SkillScriptSettlement

            self._guide = SkillScriptSettlement(
                dataclasses.replace(config.scenario, build=config.scenario.build and build), config.seed,
                self._network.inventory_size, (self._network.board_width, self._network.board_height),
                self._network.food_retry_ticks)
        self.members: dict[int, Member] = {}
        # The settlement as it stood when the warm-up ended: (tick, members). A world restored at that
        # tick (train.py --episode-ticks with a fixed warm-up) gets these members back.
        self._checkpoint: tuple[int, dict[int, Member]] | None = None
        self._last_tick = -1
        self._finished: list[tuple[list[Step], float]] = []
        self._ticks_since_update = 0
        # How often each choice was taken since the last call of drain_choice_counts().
        self.choice_counts = [0] * self.choices.count

    # --- Brain.Step -----------------------------------------------------------------------------

    def step(self, request: pb.StepRequest) -> pb.StepResponse:
        tick = request.tick
        if tick == 0 or tick <= self._last_tick:
            # A new world: from tick 0, or restored at a later tick.
            self._start_run()
            if tick > 0:
                if self._checkpoint is None or self._checkpoint[0] != tick:
                    raise ValueError(f"A world restored at tick {tick}, but the brain kept no settlement for it")
                self.members = copy.deepcopy(self._checkpoint[1])
        else:
            for npc_id in request.retired_npc_ids:
                self._retire(npc_id)

        self._last_tick = tick
        response = pb.StepResponse()
        if not request.npcs:
            return response

        # With team_share above zero part of every reward is the settlement's mean: what is good for
        # the group counts for each of its members.
        share = self._network.team_share
        team_reward = sum(view.reward for view in request.npcs) / len(request.npcs) if share > 0 else 0.0

        gamma = self._network.gamma
        situations = []
        for view in request.npcs:
            member = self.members.get(view.npc_id)
            if member is None:
                member = self.members[view.npc_id] = Member(
                    view.npc_id, npc_rng(self._config.seed, view.npc_id),
                    PlaceMap(self._network.food_retry_ticks), tick)
            observation = view.observation
            if observation.season == WINTER and member.season != WINTER:
                member.map.winter_rot(tick)
            member.season = observation.season
            if view.died or view.truncated:
                # Stages 1 and 2 move the NPC to a random cell; whatever it was heading for is void.
                member.explore_target = None
            self._credit(member, view, tick, (1 - share) * view.reward + share * team_reward, gamma)
            member.map.see(observation, tick)
            situations.append(Situation(member, observation, tick))
        self._swap_maps(request)

        if tick < self.warmup:
            return self._guided(request, situations)
        if self._learning:
            self.learned_ticks += 1

        # An NPC keeps the skill it chose for `skill_ticks` ticks, unless the skill stops being possible.
        # Only the NPCs that choose now go through the network.
        deciding, cells, scalars, masks = [], [], [], []
        for view, situation in zip(request.npcs, situations):
            member = situation.member
            note_full_shelter(member, situation, self._config.need_levels[1])
            mask = self.choices.mask(situation)
            if member.choice is not None and tick < member.decide_at and mask[member.choice]:
                continue
            self._close(member)
            deciding.append(situation)
            cells.append(list(view.observation.surroundings))
            scalars.append(self._scalars(situation, len(view.neighbour_ids)))
            masks.append(mask)

        if self._learning:
            self._ticks_since_update += 1
            if self._ticks_since_update >= self._network.rollout_ticks:
                self._update(deciding, cells, scalars, masks)

        if deciding:
            if self._learning or self._network.sample_in_show:
                chosen, log_probs, values = self.ppo.choose(cells, scalars, masks)
            else:
                logits, value_t = self.ppo.evaluate(cells, scalars, masks)
                chosen, log_probs, values = logits.argmax(1).tolist(), [0.0] * len(cells), value_t.tolist()
            for index, situation in enumerate(deciding):
                member = situation.member
                member.choice = chosen[index]
                member.decide_at = tick + self._network.skill_ticks
                self.choice_counts[member.choice] += 1
                if self._learning:
                    member.pending = Step(cells[index], scalars[index], masks[index], member.choice,
                                          log_probs[index], values[index])
                    member.discount = 1.0

        for view, situation in zip(request.npcs, situations):
            member = situation.member
            action = self.choices.action(member.choice, situation)
            if action not in MOVES and member.choice != self._stay_skill:
                # The skill has done its work (drunk, eaten, picked up, built one step): the NPC chooses
                # again next tick instead of standing there, in winter out in the cold.
                member.decide_at = tick + 1
            response.actions.add(npc_id=view.npc_id, action=action)
        return response

    def _guided(self, request: pb.StepRequest, situations) -> pb.StepResponse:
        """A warm-up tick: the scenario decides and the network neither acts nor learns."""
        response = pb.StepResponse()
        explore = self.choices.names.index("explore")
        for view, situation in zip(request.npcs, situations):
            member = situation.member
            note_full_shelter(member, situation, self._config.need_levels[1])
            mask = self.choices.mask(situation)
            skill = self._guide.choose(situation, mask)
            if not mask[skill]:
                skill = explore
            member.choice = None
            response.actions.add(npc_id=view.npc_id, action=self.choices.action(skill, situation))
        if request.tick == self.warmup - 1:
            self._checkpoint = (self.warmup, copy.deepcopy(self.members))
        return response

    def warmup_for(self, learned_ticks: int) -> int:
        """Warm-up ticks of the next world: they shrink to zero over `warmup_decay_ticks` of learning."""
        network = self._network
        if self._guide is None:
            return 0
        if network.warmup_decay_ticks <= 0:
            return network.warmup_ticks
        left = max(0.0, 1.0 - learned_ticks / network.warmup_decay_ticks)
        return int(network.warmup_ticks * left)

    def save(self) -> None:
        if self._save_path is None:
            return
        self._save_path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self._save_path.with_suffix(".tmp")
        torch.save({**self.ppo.state(), "learned_ticks": self.learned_ticks}, temporary)
        os.replace(temporary, self._save_path)

    def drain_choice_counts(self) -> dict[str, int]:
        counts = dict(zip(self.choices.names, self.choice_counts))
        self.choice_counts = [0] * self.choices.count
        return counts

    # --- experience -----------------------------------------------------------------------------

    def _credit(self, member: Member, view, tick: int, reward: float, gamma: float) -> None:
        """Adds the reward for the last tick to the decision that is still running."""
        step = member.pending
        if step is None:
            return
        step.reward += member.discount * reward
        member.discount *= gamma
        if view.died:
            step.done = True
            self._close(member)
        elif view.truncated:
            # The life limit only cuts the episode: add what the state before the reset was worth.
            if not view.HasField("final_observation"):
                raise ValueError(f"NPC {view.npc_id}: truncated view without final_observation")
            final = Situation(member, view.final_observation, tick)
            _, value = self.ppo.evaluate([list(view.final_observation.surroundings)],
                                         [self._scalars(final, len(view.neighbour_ids))],
                                         [self.choices.mask(final)])
            step.reward += member.discount * float(value[0])
            step.done = True
            self._close(member)

    @staticmethod
    def _close(member: Member) -> None:
        """The running decision is over: it becomes one step of experience."""
        step = member.pending
        member.choice = None
        if step is not None:
            step.discount = member.discount
            member.steps.append(step)
            member.pending = None

    def _retire(self, npc_id: int) -> None:
        """The NPC is gone for good. Its experience stays; the step that got no reward is dropped."""
        member = self.members.pop(npc_id, None)
        if member is not None and member.steps:
            bootstrap = member.pending.value if member.pending is not None else 0.0
            self._finished.append((member.steps, bootstrap))

    def _start_run(self) -> None:
        """Tick 0: a new world. What the NPCs of the previous one experienced still counts."""
        for npc_id in list(self.members):
            self._retire(npc_id)
        self.warmup = self.warmup_for(self.learned_ticks)

    def _update(self, deciding, cells, scalars, masks) -> None:
        """One PPO update. A piece of experience ends with the value of the state that follows it: for an
        NPC in the middle of a skill that is the value noted when the skill began."""
        pieces = self._finished
        fresh = {}
        if deciding:
            _, values = self.ppo.evaluate(cells, scalars, masks)
            fresh = {situation.member.npc_id: value for situation, value in zip(deciding, values.tolist())}
        for member in self.members.values():
            if member.steps:
                bootstrap = member.pending.value if member.pending is not None else fresh.get(member.npc_id, 0.0)
                pieces.append((member.steps, bootstrap))
                member.steps = []
        self.ppo.update(pieces)
        self._finished = []
        self._ticks_since_update = 0

    # --- inputs ---------------------------------------------------------------------------------

    def _scalars(self, s: Situation, neighbours: int) -> list[float]:
        obs, network, member = s.obs, self._network, s.member
        season = [0.0] * 5
        season[obs.season if 0 <= obs.season <= 4 else 0] = 1.0
        row = [
            obs.hunger / 100, obs.thirst / 100, obs.energy / 100,
            obs.carried_food / network.inventory_size, obs.carried_wood / network.inventory_size,
            *season,
            obs.season_ticks_left / network.season_ticks if network.season_ticks else 0.0,
            min(1.0, obs.here_amount / 20),
            min(1.0, neighbours / 8),
            min(1.0, (s.tick - member.born_tick) / network.age_scale),
        ]
        x, y = obs.x, obs.y
        for kind in PLACE_INPUTS:
            place = s.near(kind)
            if place is None:
                row += [0.0, 0.0, 0.0, 0.0]
            else:
                dx, dy = place[0] - x, place[1] - y
                distance = abs(dx) + abs(dy)
                row += [1.0, dx / distance if distance else 0.0, dy / distance if distance else 0.0,
                        min(1.0, distance / DISTANCE_SCALE)]
        place_map = member.map
        shelters = place_map.by_type.get(pb.CELL_SHELTER, ())
        stores = place_map.by_type.get(pb.CELL_STORE, ())
        row.append(min(1.0, len(shelters) / 10))
        row.append(min(1.0, sum(place_map.amount(p) for p in stores) / 100))
        return row

    def _swap_maps(self, request: pb.StepRequest) -> None:
        for view in request.npcs:
            mine = self.members[view.npc_id]
            for neighbour_id in view.neighbour_ids:
                theirs = self.members.get(neighbour_id)
                # Each pair shows up twice (once per side); swap once, in both directions.
                if theirs is not None and view.npc_id < neighbour_id:
                    mine.map.merge(theirs.map, neighbour_id)
                    theirs.map.merge(mine.map, view.npc_id)
