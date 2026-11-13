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
class SharingConfig:
    """Stage 2: how knowledge passes between neighbours. Everything is off by default (stage 1)."""

    inherit: bool = False
    inherit_trust: float = 0.25
    observe: bool = False
    observe_weight: float = 0.5
    map: bool = False
    map_food_retry_ticks: int = 50


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
    sharing: SharingConfig = SharingConfig()
    scenario: "ScenarioConfig | None" = None   # stage 3: the hand-written settlement


def load_config(path: Path = DEFAULT_CONFIG) -> BrainConfig:
    with open(path, encoding="utf-8") as file:
        root = yaml.safe_load(file)
    brain = root["brain"]
    low, high = brain["need_levels"]
    sharing = brain.get("sharing", {})
    scenario = None
    if "scripted" in brain:
        from brain.scripted import ScenarioConfig

        economy = root["economy"]
        scenario = ScenarioConfig(
            build=bool(brain["scripted"]["build"]),
            season_ticks=int(root["seasons"]["season_ticks"]),
            consume_relief=int(root["world"]["consume_relief"]),
            shelter_capacity=int(economy["shelter_capacity"]),
            store_capacity=int(economy["store_capacity"]),
            shelter_wood=int(economy["shelter_wood"]),
            store_wood=int(economy["store_wood"]),
        )
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
        sharing=SharingConfig(
            inherit=bool(sharing.get("inherit", False)),
            inherit_trust=float(sharing.get("inherit_trust", 0.25)),
            observe=bool(sharing.get("observe", False)),
            observe_weight=float(sharing.get("observe_weight", 0.5)),
            map=bool(sharing.get("map", False)),
            map_food_retry_ticks=int(sharing.get("map_food_retry_ticks", 50)),
        ),
        scenario=scenario,
    )
