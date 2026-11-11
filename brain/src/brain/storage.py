"""Saving and loading what the brains have learned (pickle, one file for the whole group)."""

import os
import pickle
from pathlib import Path

FORMAT_VERSION = 1


def save_brains(path: Path, brains: dict) -> None:
    """Writes the state of every brain that can export it. The file is replaced atomically."""
    states = {npc_id: brain.export_state() for npc_id, brain in brains.items() if hasattr(brain, "export_state")}
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with open(temporary, "wb") as file:
        pickle.dump({"version": FORMAT_VERSION, "npcs": states}, file, protocol=pickle.HIGHEST_PROTOCOL)
    os.replace(temporary, path)


def load_brains(path: Path) -> dict[int, dict]:
    """Returns saved states by NPC id, or an empty dict when there is no file yet.

    Only load files written by save_brains: pickle executes code from the file it reads.
    """
    if not path.exists():
        return {}
    with open(path, "rb") as file:
        data = pickle.load(file)
    if data.get("version") != FORMAT_VERSION:
        raise ValueError(f"{path}: unsupported format version {data.get('version')!r}")
    return data["npcs"]
