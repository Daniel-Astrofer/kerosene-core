from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "tests" / "integration" / "docker-compose.yml"
TOKEN = "x" * 32


pytestmark = pytest.mark.integration


def docker_compose_cmd() -> list[str]:
    if subprocess.run(["docker", "compose", "version"], capture_output=True).returncode == 0:
        return ["docker", "compose", "-f", str(COMPOSE)]
    return ["docker-compose", "-f", str(COMPOSE)]


@pytest.fixture(scope="module")
def integration_stack():
    if os.getenv("RUN_DOCKER_INTEGRATION") != "1":
        pytest.skip("Set RUN_DOCKER_INTEGRATION=1 to run docker-compose integration tests")

    cmd = docker_compose_cmd()
    subprocess.run([*cmd, "up", "-d", "--build"], check=True)
    try:
        wait_for("http://127.0.0.1:18090/health")
        wait_for("http://127.0.0.1:18091/health")
        yield
    finally:
        subprocess.run([*cmd, "down", "-v"], check=False)


def wait_for(url: str, timeout: float = 90.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            request_json("GET", url)
            return
        except Exception:
            time.sleep(1)
    raise TimeoutError(f"Timed out waiting for {url}")


def request_json(method: str, url: str, body: dict | None = None, auth: bool = False) -> tuple[int, dict]:
    data = None if body is None else json.dumps(body).encode()
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if auth:
        headers["Authorization"] = f"Bearer {TOKEN}"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode())


def test_bitcoin_core_flask_against_containerized_rpc_stub(integration_stack):
    status, node = request_json("GET", "http://127.0.0.1:18090/v1/node/status", auth=True)
    psbt_status, psbt = request_json(
        "POST",
        "http://127.0.0.1:18090/v1/wallets/ops/transactions/psbt",
        {"outputs": [{"address": "bcrt1qintegrationaddress0000000000000000000", "amount_btc": "0.001"}]},
        auth=True,
    )
    broadcast_status, broadcast = request_json(
        "POST",
        "http://127.0.0.1:18090/v1/wallets/ops/transactions/broadcast",
        {"raw_tx_hex": "0200000001" + "00" * 120},
        auth=True,
    )

    assert status == 200
    assert node["node"]["chain"] == "regtest"
    assert psbt_status == 201
    assert psbt["psbt"] == "cHNidP8BAHECAAAAA"
    assert broadcast_status == 201
    assert broadcast["txid"] == "c" * 64


def test_lightning_flask_against_containerized_lnd_stub(integration_stack):
    status, node = request_json("GET", "http://127.0.0.1:18091/v1/node/status", auth=True)
    invoice_status, invoice = request_json(
        "POST",
        "http://127.0.0.1:18091/v1/invoices",
        {"amount_sats": 2500, "memo": "integration"},
        auth=True,
    )
    payment_status, payment = request_json(
        "POST",
        "http://127.0.0.1:18091/v1/payments",
        {"payment_request": "lnbcrt1" + "p" * 80, "fee_limit_sats": 10},
        auth=True,
    )

    assert status == 200
    assert node["node"]["alias"] == "integration-lnd"
    assert invoice_status == 201
    assert invoice["invoice"]["payment_hash"] == "d" * 64
    assert payment_status == 202
    assert payment["payment"]["status"] == "submitted"
