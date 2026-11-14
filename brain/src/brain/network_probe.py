"""Step 3.7: an untrained network that answers Brain.Step, to measure what a network costs per tick.

It encodes every observation the way a stage 3 network would (one-hot cells of the view, needs,
inventory, season) and runs one forward pass of a small MLP for the whole group per tick.
Nothing learns. Needs the optional `nn` dependency group: `uv sync --group nn`.
"""

from brain.proto import brain_pb2 as pb

CELL_TYPES = 14          # CELL_UNSPECIFIED .. CELL_STORE
ACTIONS = 13             # ACTION_STAY .. ACTION_WITHDRAW (contract values 1..13)
SCALARS = 9


class NetworkProbe:
    """One MLP for the whole settlement, as planned for step 3.9."""

    def __init__(self, seed: int, view_size: int = 7, hidden: int = 128):
        import torch

        torch.manual_seed(seed)
        torch.set_num_threads(1)
        self._torch = torch
        self._view_cells = view_size * view_size
        inputs = self._view_cells * CELL_TYPES + SCALARS
        self.network = torch.nn.Sequential(
            torch.nn.Linear(inputs, hidden), torch.nn.Tanh(),
            torch.nn.Linear(hidden, hidden), torch.nn.Tanh(),
            torch.nn.Linear(hidden, ACTIONS),
        )
        self.network.eval()
        self._generator = torch.Generator().manual_seed(seed)

    def step(self, request: pb.StepRequest) -> pb.StepResponse:
        torch = self._torch
        response = pb.StepResponse()
        if not request.npcs:
            return response
        cells = torch.tensor([list(view.observation.surroundings) for view in request.npcs], dtype=torch.long)
        one_hot = torch.nn.functional.one_hot(cells, CELL_TYPES).float().flatten(1)
        scalars = torch.tensor([[
            o.hunger / 100, o.thirst / 100, o.energy / 100, o.carried_food / 4, o.carried_wood / 4,
            o.season / 4, o.season_ticks_left / 500, o.here_amount / 100, len(view.neighbour_ids) / 8,
        ] for view in request.npcs for o in [view.observation]])
        with torch.no_grad():
            logits = self.network(torch.cat([one_hot, scalars], dim=1))
            actions = torch.multinomial(torch.softmax(logits, dim=1), 1, generator=self._generator).flatten()
        for view, action in zip(request.npcs, actions.tolist()):
            response.actions.add(npc_id=view.npc_id, action=action + 1)
        return response

    def save(self) -> None:
        """Nothing to save: the probe does not learn."""
