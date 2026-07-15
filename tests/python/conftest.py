from __future__ import annotations

import importlib
import sys
from pathlib import Path
from typing import Any

import pytest


ROOT = Path(__file__).resolve().parents[3]
BITCOIN_FLASK = ROOT / "adapters" / "bitcoin_core_flask"
LIGHTNING_FLASK = ROOT / "adapters" / "lightning_flask"


SERVICE_MODULES = {
    "app",
    "bitcoin_core",
    "cohesion",
    "config",
    "lnd",
    "security",
}


def clear_service_modules() -> None:
    for name in SERVICE_MODULES:
        sys.modules.pop(name, None)


def import_service(service_dir: Path, module_name: str) -> Any:
    """Import one Flask backend at a time; both services use top-level module names."""
    clear_service_modules()
    sys.path.insert(0, str(service_dir))
    try:
        return importlib.import_module(module_name)
    finally:
        try:
            sys.path.remove(str(service_dir))
        except ValueError:
            pass


@pytest.fixture
def bearer_headers() -> dict[str, str]:
    return {"Authorization": "Bearer " + "x" * 32}


@pytest.fixture
def bitcoin_modules() -> dict[str, Any]:
    clear_service_modules()
    sys.path.insert(0, str(BITCOIN_FLASK))
    try:
        return {
            "config": importlib.import_module("config"),
            "security": importlib.import_module("security"),
            "bitcoin_core": importlib.import_module("bitcoin_core"),
            "app": importlib.import_module("app"),
        }
    finally:
        sys.path.remove(str(BITCOIN_FLASK))


@pytest.fixture
def lightning_modules() -> dict[str, Any]:
    clear_service_modules()
    sys.path.insert(0, str(LIGHTNING_FLASK))
    try:
        return {
            "config": importlib.import_module("config"),
            "security": importlib.import_module("security"),
            "lnd": importlib.import_module("lnd"),
            "app": importlib.import_module("app"),
        }
    finally:
        sys.path.remove(str(LIGHTNING_FLASK))
