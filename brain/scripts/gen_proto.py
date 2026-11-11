"""Generate Python code from proto/brain.proto into src/brain/proto/.

Usage (from the brain/ directory): uv run python scripts/gen_proto.py
"""

import sys
from importlib import resources
from pathlib import Path

from grpc_tools import protoc

BRAIN_DIR = Path(__file__).resolve().parent.parent
PROTO_DIR = BRAIN_DIR.parent / "proto"
OUT_DIR = BRAIN_DIR / "src"

# Maps proto/brain.proto to the virtual path brain/proto/brain.proto, so the generated
# modules land in the brain.proto package and import each other as `from brain.proto import ...`.
VIRTUAL_PACKAGE = "brain/proto"


def main() -> int:
    well_known_protos = resources.files("grpc_tools") / "_proto"
    args = [
        "grpc_tools.protoc",
        f"-I{VIRTUAL_PACKAGE}={PROTO_DIR}",
        f"-I{well_known_protos}",
        f"--python_out={OUT_DIR}",
        f"--pyi_out={OUT_DIR}",
        f"--grpc_python_out={OUT_DIR}",
        f"{VIRTUAL_PACKAGE}/brain.proto",
    ]
    exit_code = protoc.main(args)
    if exit_code == 0:
        print(f"Generated brain.proto code in {OUT_DIR / VIRTUAL_PACKAGE}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
