from __future__ import annotations

import base64
import io
import json
import tempfile
import urllib.error
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any

import pytest


VALID_ADDRESS = "bcrt1qexampleaddress00000000000000000000000"
RAW_TX_HEX = "0200000001" + "00" * 120
TXID = "b" * 64


@dataclass
class FakeBitcoinRpc:
    blocks: int = 101
    wallets: list[str] = field(default_factory=lambda: ["ops"])
    mempool: dict[str, dict[str, Any]] = field(default_factory=dict)
    confirmed: dict[str, dict[str, Any]] = field(default_factory=dict)
    wallet_creates: int = 0
    psbt_calls: list[dict[str, Any]] = field(default_factory=list)
    broadcasts: list[str] = field(default_factory=list)

    def node_status(self):
        return {"chain": "regtest", "blocks": self.blocks, "headers": self.blocks, "mempool_tx_count": len(self.mempool)}

    def list_wallets(self):
        return {"loaded": self.wallets, "available": self.wallets}

    def create_wallet(self, wallet, disable_private_keys=False, blank=False, descriptors=True):
        self.wallet_creates += 1
        if wallet not in self.wallets:
            self.wallets.append(wallet)
        return {"name": wallet, "warning": ""}

    def wallet_balance(self, wallet):
        confirmed = sum(Decimal(str(tx["amount_btc"])) for tx in self.confirmed.values() if tx["wallet"] == wallet)
        pending = sum(Decimal(str(tx["amount_btc"])) for tx in self.mempool.values() if tx["wallet"] == wallet)
        return {
            "trusted_btc": format(confirmed, "f"),
            "untrusted_pending_btc": format(pending, "f"),
            "immature_btc": 0,
        }

    def new_address(self, wallet, label="", address_type="bech32"):
        return {"address": VALID_ADDRESS, "address_type": address_type, "label": label}

    def list_transactions(self, wallet, count=25, skip=0):
        txs = [
            {"txid": txid, "category": "receive", "confirmations": tx["confirmations"], "amount": tx["amount_btc"]}
            for txid, tx in {**self.mempool, **self.confirmed}.items()
            if tx["wallet"] == wallet
        ]
        return {"transactions": txs[skip : skip + count], "count": min(len(txs), count), "skip": skip}

    def funded_psbt(self, wallet, outputs, fee_rate_sat_vb=None, lock_unspents=False):
        self.psbt_calls.append(
            {"wallet": wallet, "outputs": outputs, "fee_rate_sat_vb": fee_rate_sat_vb, "lock_unspents": lock_unspents}
        )
        return {"psbt": "cHNidP8BAHECAAAAA", "fee_btc": "0.00001000", "change_position": 1}

    def broadcast_raw(self, raw_tx_hex):
        self.broadcasts.append(raw_tx_hex)
        self.mempool[TXID] = {"wallet": "ops", "amount_btc": "0.001", "confirmations": 0}
        return {"txid": TXID}

    def mine(self, txid: str, confirmations: int = 6):
        tx = self.mempool.pop(txid)
        tx["confirmations"] = confirmations
        self.confirmed[txid] = tx
        self.blocks += confirmations


@pytest.fixture
def bitcoin_app(tmp_path, bitcoin_modules):
    Settings = bitcoin_modules["config"].Settings
    create_app = bitcoin_modules["app"].create_app
    rpc = FakeBitcoinRpc()
    settings = Settings(
        api_token="x" * 32,
        rpc_url="http://127.0.0.1:18443",
        rpc_user="user",
        rpc_password="password",
        sqlite_path=str(tmp_path / "bitcoin.sqlite3"),
        rate_limit_per_minute=1_000,
        max_body_bytes=4096,
    )
    return create_app(settings, rpc).test_client(), rpc


def test_health_is_public_and_protected_routes_require_auth(bitcoin_app):
    client, _rpc = bitcoin_app

    assert client.get("/health").status_code == 200
    response = client.get("/v1/wallets")

    assert response.status_code == 401
    assert response.get_json()["error"]["code"] == "unauthorized"


def test_wallet_lifecycle_and_audit_snapshot(bitcoin_app, bearer_headers):
    client, rpc = bitcoin_app
    headers = {**bearer_headers, "Idempotency-Key": "wallet-ops-1"}

    created = client.post("/v1/wallets", json={"wallet": "ops"}, headers=headers)
    replayed = client.post("/v1/wallets", json={"wallet": "ops"}, headers=headers)
    wallets = client.get("/v1/wallets", headers=bearer_headers)
    snapshot = client.get("/v1/cohesion/snapshot", headers=bearer_headers)

    assert created.status_code == 201
    assert replayed.status_code == 201
    assert created.get_json() == replayed.get_json()
    assert rpc.wallet_creates == 1
    assert wallets.get_json()["wallets"]["loaded"] == ["ops"]
    assert snapshot.get_json()["cohesion"]["audit_events"] == 1


def test_idempotency_conflict_returns_409(bitcoin_app, bearer_headers):
    client, _rpc = bitcoin_app
    headers = {**bearer_headers, "Idempotency-Key": "same-key"}

    first = client.post("/v1/wallets", json={"wallet": "ops"}, headers=headers)
    second = client.post("/v1/wallets", json={"wallet": "treasury"}, headers=headers)

    assert first.status_code == 201
    assert second.status_code == 409
    assert second.get_json()["error"]["code"] == "idempotency_conflict"


def test_create_psbt_validates_outputs_and_preserves_rpc_shape(bitcoin_app, bearer_headers):
    client, rpc = bitcoin_app

    response = client.post(
        "/v1/wallets/ops/transactions/psbt",
        json={
            "outputs": [{"address": VALID_ADDRESS, "amount_btc": "0.00100000"}],
            "fee_rate_sat_vb": 12,
            "lock_unspents": True,
        },
        headers=bearer_headers,
    )

    assert response.status_code == 201
    assert response.get_json()["psbt"] == "cHNidP8BAHECAAAAA"
    assert rpc.psbt_calls == [
        {
            "wallet": "ops",
            "outputs": [{VALID_ADDRESS: "0.00100000"}],
            "fee_rate_sat_vb": 12,
            "lock_unspents": True,
        }
    ]


@pytest.mark.parametrize(
    ("payload", "code"),
    [
        ({"outputs": []}, "invalid_outputs"),
        ({"outputs": [{"address": "bad", "amount_btc": "0.001"}]}, "invalid_address"),
        ({"outputs": [{"address": VALID_ADDRESS, "amount_btc": "0"}]}, "invalid_amount"),
        ({"outputs": [{"address": VALID_ADDRESS, "amount_btc": "0.000000001"}]}, "invalid_amount"),
        ({"outputs": [{"address": VALID_ADDRESS, "amount_btc": "0.001"}], "fee_rate_sat_vb": "fast"}, "invalid_integer"),
    ],
)
def test_psbt_edge_case_validation(bitcoin_app, bearer_headers, payload, code):
    client, _rpc = bitcoin_app

    response = client.post("/v1/wallets/ops/transactions/psbt", json=payload, headers=bearer_headers)

    assert response.status_code == 400
    assert response.get_json()["error"]["code"] == code


def test_broadcast_simulates_mempool_then_confirmations(bitcoin_app, bearer_headers):
    client, rpc = bitcoin_app

    broadcast = client.post(
        "/v1/wallets/ops/transactions/broadcast",
        json={"raw_tx_hex": RAW_TX_HEX},
        headers=bearer_headers,
    )
    pending = client.get("/v1/wallets/ops/balance", headers=bearer_headers)
    rpc.mine(TXID, confirmations=6)
    confirmed = client.get("/v1/wallets/ops/balance", headers=bearer_headers)
    txs = client.get("/v1/wallets/ops/transactions?count=10&skip=0", headers=bearer_headers)

    assert broadcast.status_code == 201
    assert broadcast.get_json()["txid"] == TXID
    assert pending.get_json()["balance"]["untrusted_pending_btc"] == "0.001"
    assert confirmed.get_json()["balance"]["trusted_btc"] == "0.001"
    assert txs.get_json()["transactions"][0]["confirmations"] == 6


def test_rejects_unsupported_media_type_and_oversized_body(bitcoin_app, bearer_headers):
    client, _rpc = bitcoin_app

    wrong_type = client.post(
        "/v1/wallets",
        data="wallet=ops",
        headers={**bearer_headers, "Content-Type": "text/plain"},
    )
    too_large = client.post(
        "/v1/wallets",
        data=json.dumps({"wallet": "ops", "padding": "x" * 5000}),
        headers={**bearer_headers, "Content-Type": "application/json"},
    )

    assert wrong_type.status_code == 415
    assert too_large.status_code == 413


def test_rate_limit_is_enforced(tmp_path, bitcoin_modules, bearer_headers):
    Settings = bitcoin_modules["config"].Settings
    create_app = bitcoin_modules["app"].create_app
    settings = Settings(
        api_token="x" * 32,
        rpc_url="http://127.0.0.1:18443",
        rpc_user="user",
        rpc_password="password",
        sqlite_path=str(tmp_path / "limited.sqlite3"),
        rate_limit_per_minute=1,
    )
    client = create_app(settings, FakeBitcoinRpc()).test_client()

    assert client.get("/v1/node/status", headers=bearer_headers).status_code == 200
    assert client.get("/v1/node/status", headers=bearer_headers).status_code == 429


class RpcResponse:
    def __init__(self, payload: Any):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False

    def read(self, _limit: int):
        return json.dumps(self.payload).encode()


def test_bitcoin_core_client_batches_status_and_caches(monkeypatch, tmp_path, bitcoin_modules):
    Settings = bitcoin_modules["config"].Settings
    BitcoinCoreClient = bitcoin_modules["bitcoin_core"].BitcoinCoreClient
    calls = []

    def fake_urlopen(request, timeout):
        calls.append((request.full_url, request.headers, json.loads(request.data.decode())))
        return RpcResponse(
            [
                {"id": 2, "error": None, "result": {"version": 260000, "connections": 8}},
                {"id": 1, "error": None, "result": {"chain": "regtest", "blocks": 42, "headers": 42}},
                {"id": 3, "error": None, "result": {"size": 2, "bytes": 500}},
            ]
        )

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    client = BitcoinCoreClient(
        Settings(
            api_token="x" * 32,
            rpc_url="http://node.local:18443/",
            rpc_user="rpcuser",
            rpc_password="rpcpass",
            sqlite_path=str(tmp_path / "unused.sqlite3"),
            status_cache_seconds=60,
        )
    )

    first = client.node_status()
    second = client.node_status()

    assert first == second
    assert first["chain"] == "regtest"
    assert len(calls) == 1
    assert calls[0][1]["Authorization"] == "Basic " + base64.b64encode(b"rpcuser:rpcpass").decode()


def test_bitcoin_core_client_maps_rpc_errors(monkeypatch, tmp_path, bitcoin_modules):
    Settings = bitcoin_modules["config"].Settings
    BitcoinCoreClient = bitcoin_modules["bitcoin_core"].BitcoinCoreClient
    ApiError = bitcoin_modules["security"].ApiError

    def fake_urlopen(_request, _timeout):
        raise urllib.error.URLError("connection refused")

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    client = BitcoinCoreClient(
        Settings(
            api_token="x" * 32,
            rpc_url="http://node.local:18443",
            rpc_user="rpcuser",
            rpc_password="rpcpass",
            sqlite_path=str(tmp_path / "unused.sqlite3"),
        )
    )

    with pytest.raises(ApiError) as error:
        client.list_wallets()

    assert error.value.status_code == 503
    assert error.value.code == "rpc_unavailable"


def test_bitcoin_core_client_surfaces_http_error_body(monkeypatch, tmp_path, bitcoin_modules):
    Settings = bitcoin_modules["config"].Settings
    BitcoinCoreClient = bitcoin_modules["bitcoin_core"].BitcoinCoreClient
    ApiError = bitcoin_modules["security"].ApiError

    def fake_urlopen(_request, _timeout):
        body = io.BytesIO(b'{"error":{"message":"insufficient fee"}}')
        raise urllib.error.HTTPError("http://node", 500, "RPC error", {}, body)

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    client = BitcoinCoreClient(
        Settings(
            api_token="x" * 32,
            rpc_url="http://node.local:18443",
            rpc_user="rpcuser",
            rpc_password="rpcpass",
            sqlite_path=str(tmp_path / "unused.sqlite3"),
        )
    )

    with pytest.raises(ApiError) as error:
        client.broadcast_raw(RAW_TX_HEX)

    assert error.value.status_code == 502
    assert "insufficient fee" in error.value.message
