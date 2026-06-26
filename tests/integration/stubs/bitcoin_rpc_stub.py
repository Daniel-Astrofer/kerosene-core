from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


TXID = "c" * 64
WALLETS = ["ops"]
MEMPOOL = {}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length) or b"{}")
        response = [self.handle_call(item) for item in payload] if isinstance(payload, list) else self.handle_call(payload)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(response).encode())

    def log_message(self, _format, *_args):
        return

    def handle_call(self, call):
        method = call.get("method")
        params = call.get("params", [])
        result = None
        error = None
        try:
            result = dispatch(method, params)
        except Exception as exc:
            error = {"code": -1, "message": str(exc)}
        return {"jsonrpc": "1.0", "id": call.get("id"), "result": result, "error": error}


def dispatch(method, params):
    if method == "getblockchaininfo":
        return {"chain": "regtest", "blocks": 101, "headers": 101, "verificationprogress": 1, "pruned": False}
    if method == "getnetworkinfo":
        return {"version": 260000, "connections": 8}
    if method == "getmempoolinfo":
        return {"size": len(MEMPOOL), "bytes": len(MEMPOOL) * 250}
    if method == "listwallets":
        return WALLETS
    if method == "listwalletdir":
        return {"wallets": [{"name": wallet} for wallet in WALLETS]}
    if method == "createwallet":
        wallet = params[0]
        if wallet not in WALLETS:
            WALLETS.append(wallet)
        return {"name": wallet}
    if method == "getbalances":
        return {"mine": {"trusted": 1.25, "untrusted_pending": 0.001 if MEMPOOL else 0, "immature": 0}}
    if method == "getnewaddress":
        return "bcrt1qintegrationaddress0000000000000000000"
    if method == "listtransactions":
        return [{"txid": txid, "confirmations": 0, "amount": tx["amount"]} for txid, tx in MEMPOOL.items()]
    if method == "walletcreatefundedpsbt":
        return {"psbt": "cHNidP8BAHECAAAAA", "fee": 0.00001, "changepos": 1}
    if method == "sendrawtransaction":
        MEMPOOL[TXID] = {"amount": 0.001}
        return TXID
    raise ValueError(f"unsupported method {method}")


ThreadingHTTPServer(("0.0.0.0", 18443), Handler).serve_forever()
