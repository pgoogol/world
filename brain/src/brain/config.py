"""Brain-side view of the stage configuration file shared with the world."""

from dataclasses import dataclass
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
BRAIN_DIR = REPO_ROOT / "brain"
DEFAULT_CONFIG = REPO_ROOT / "config" / "stage-1.yaml"


@dataclass(frozen=True)
class FixedTuningConfig:
    alpha: float
    epsilon_start: float
    epsilon_end: float
    epsilon_decay_ticks: int


@dataclass(frozen=True)
class SelfTuningConfig:
    epsilon_min: float
    alpha_min: float


@dataclass(frozen=True)
class BrainConfig:
    seed: int
    address: str
    kind: str  # "qlearning" | "random"
    tuning: str  # "self" | "fixed"
    gamma: float
    need_levels: tuple[int, int]
    fixed: FixedTuningConfig
    self_tuning: SelfTuningConfig
    save_every_ticks: int
    data_dir: str


def load_config(path: Path = DEFAULT_CONFIG) -> BrainConfig:
    with open(path, encoding="utf-8") as file:
        root = yaml.safe_load(file)
    brain = root["brain"]
    low, high = brain["need_levels"]
    return BrainConfig(
        seed=int(root["seed"]),
        address=brain["address"],
        kind=brain["kind"],
        tuning=brain["tuning"],
        gamma=float(brain["gamma"]),
        need_levels=(int(low), int(high)),
        fixed=FixedTuningConfig(
            alpha=float(brain["fixed"]["alpha"]),
            epsilon_start=float(brain["fixed"]["epsilon_start"]),
            epsilon_end=float(brain["fixed"]["epsilon_end"]),
            epsilon_decay_ticks=int(brain["fixed"]["epsilon_decay_ticks"]),
        ),
        self_tuning=SelfTuningConfig(
            epsilon_min=float(brain["self_tuning"]["epsilon_min"]),
            alpha_min=float(brain["self_tuning"]["alpha_min"]),
        ),
        save_every_ticks=int(brain["save_every_ticks"]),
        data_dir=brain["data_dir"],
    )
