"""One small actor-critic network for the whole settlement, trained with PPO.

Needs the optional `nn` dependency group (PyTorch). Everything random draws from generators seeded
from the configuration and the network runs on one thread, so the same seed gives the same run
on the same machine.
"""

from dataclasses import dataclass

import torch
from torch import nn

from brain.config import NetworkConfig

CELL_TYPES = 14  # CELL_UNSPECIFIED .. CELL_STORE


@dataclass
class Step:
    """One decision of one NPC and what came of it."""

    cells: list[int]
    scalars: list[float]
    mask: list[bool]
    choice: int
    log_prob: float
    value: float
    reward: float = 0.0     # rewards of all ticks the decision lasted, discounted to its first tick
    done: bool = False
    discount: float = 1.0   # gamma to the power of the ticks the decision lasted


def advantages_of(trajectory: list[Step], bootstrap: float, gae_lambda: float) -> list[float]:
    """Generalised advantage estimates for steps in order; `bootstrap` is the value after the last step.

    A step marked `done` ends a life: nothing that comes after it counts towards it.
    """
    result = [0.0] * len(trajectory)
    advantage = 0.0
    next_value = bootstrap
    for index in range(len(trajectory) - 1, -1, -1):
        step = trajectory[index]
        if step.done:
            next_value = 0.0
            advantage = 0.0
        delta = step.reward + step.discount * next_value - step.value
        advantage = delta + step.discount * gae_lambda * advantage
        result[index] = advantage
        next_value = step.value
    return result


class ActorCritic(nn.Module):
    """Policy and value with separate bodies: value targets reach far ahead and must not drown the policy."""

    def __init__(self, view_cells: int, scalars: int, choices: int, hidden: int):
        super().__init__()
        inputs = view_cells * CELL_TYPES + scalars
        self.body = nn.Sequential(nn.Linear(inputs, hidden), nn.Tanh(), nn.Linear(hidden, hidden), nn.Tanh())
        self.policy = nn.Linear(hidden, choices)
        self.value_body = nn.Sequential(nn.Linear(inputs, hidden), nn.Tanh(), nn.Linear(hidden, hidden), nn.Tanh())
        self.value = nn.Linear(hidden, 1)
        # A small policy head starts from an almost uniform choice.
        nn.init.orthogonal_(self.policy.weight, 0.01)
        nn.init.zeros_(self.policy.bias)

    def forward(self, cells: torch.Tensor, scalars: torch.Tensor, mask: torch.Tensor):
        inputs = torch.cat([nn.functional.one_hot(cells, CELL_TYPES).float().flatten(1), scalars], dim=1)
        logits = self.policy(self.body(inputs)).masked_fill(~mask, -1e9)
        return logits, self.value(self.value_body(inputs)).squeeze(1)


class Ppo:
    def __init__(self, view_cells: int, scalars: int, choices: int, config: NetworkConfig, seed: int):
        torch.set_num_threads(1)
        torch.use_deterministic_algorithms(True)
        torch.manual_seed(seed)
        self.config = config
        self.network = ActorCritic(view_cells, scalars, choices, config.hidden)
        self.optimizer = torch.optim.Adam(self.network.parameters(), lr=config.learning_rate, eps=1e-5)

        self._generator = torch.Generator().manual_seed(seed)
        self.updates = 0
        self.last_stats: dict[str, float] = {}

    def evaluate(self, cells: list[list[int]], scalars: list[list[float]], masks: list[list[bool]]):
        """Logits and values for a batch of observations, without gradients."""
        with torch.no_grad():
            return self.network(torch.tensor(cells, dtype=torch.long), torch.tensor(scalars, dtype=torch.float32),
                                torch.tensor(masks, dtype=torch.bool))

    def choose(self, cells, scalars, masks) -> tuple[list[int], list[float], list[float]]:
        """Samples one choice per row. Returns choices, their log-probabilities and the state values."""
        logits, values = self.evaluate(cells, scalars, masks)
        log_probs = torch.log_softmax(logits, dim=1)
        choices = torch.multinomial(log_probs.exp(), 1, generator=self._generator)
        return choices.flatten().tolist(), log_probs.gather(1, choices).flatten().tolist(), values.tolist()

    def update(self, trajectories: list[tuple[list[Step], float]]) -> None:
        """One PPO update from finished pieces of experience: (steps in order, value after the last step)."""
        config = self.config
        steps: list[Step] = []
        advantages: list[float] = []
        returns: list[float] = []
        for trajectory, bootstrap in trajectories:
            piece = advantages_of(trajectory, bootstrap, config.gae_lambda)
            steps.extend(trajectory)
            advantages.extend(piece)
            returns.extend(a + s.value for a, s in zip(piece, trajectory))
        if len(steps) < 2:
            return

        cells = torch.tensor([s.cells for s in steps], dtype=torch.long)
        scalars = torch.tensor([s.scalars for s in steps], dtype=torch.float32)
        masks = torch.tensor([s.mask for s in steps], dtype=torch.bool)
        choices = torch.tensor([s.choice for s in steps], dtype=torch.long).unsqueeze(1)
        old_log_probs = torch.tensor([s.log_prob for s in steps], dtype=torch.float32)
        advantage_t = torch.tensor(advantages, dtype=torch.float32)
        return_t = torch.tensor(returns, dtype=torch.float32)
        advantage_t = (advantage_t - advantage_t.mean()) / (advantage_t.std() + 1e-8)

        count = len(steps)
        for _ in range(config.epochs):
            order = torch.randperm(count, generator=self._generator)
            for start in range(0, count, config.minibatch):
                batch = order[start:start + config.minibatch]
                if len(batch) < 2:
                    continue
                logits, values = self.network(cells[batch], scalars[batch], masks[batch])
                log_probs = torch.log_softmax(logits, dim=1)
                chosen = log_probs.gather(1, choices[batch]).squeeze(1)
                ratio = (chosen - old_log_probs[batch]).exp()
                adv = advantage_t[batch]
                policy_loss = -torch.min(ratio * adv, ratio.clamp(1 - config.clip, 1 + config.clip) * adv).mean()
                value_loss = (values - return_t[batch]).pow(2).mean()
                entropy = -(log_probs.exp() * log_probs.clamp(min=-50)).sum(1).mean()
                loss = policy_loss + config.value_weight * value_loss - config.entropy * entropy
                self.optimizer.zero_grad()
                loss.backward()
                nn.utils.clip_grad_norm_(self.network.parameters(), config.max_grad_norm)
                self.optimizer.step()
        self.updates += 1
        self.last_stats = {"steps": count, "value_loss": value_loss.item(), "entropy": entropy.item(),
                           "mean_return": float(return_t.mean())}

    def state(self) -> dict:
        return {"network": self.network.state_dict(), "optimizer": self.optimizer.state_dict(),
                "generator": self._generator.get_state(), "updates": self.updates}

    def load_state(self, state: dict) -> None:
        self.network.load_state_dict(state["network"])
        self.optimizer.load_state_dict(state["optimizer"])
        self._generator.set_state(state["generator"])
        self.updates = state["updates"]
