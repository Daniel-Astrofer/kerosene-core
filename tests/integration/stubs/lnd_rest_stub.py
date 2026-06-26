from __future__ import annotations

import base64
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


PAYMENT_HASH = "d" * 64
INVOICE = "lnbcrt1" + "p" * 80
INVOICES = {}
PAYMENTS = {}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        body = route_get(parsed.path, parse_qs(parsed.query))
        self.respond(200, body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length) or b"{}")
        body = route_post(urlparse(self.path).path, payload)
        self.respond(200, body)

    def respond(self, status, body):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(body).encode())

    def log_message(self, _format, *_args):
        return


def route_get(path, query):
    if path == "/v1/getinfo":
        return {"identity_pubkey": "02" + "e" * 64, "alias": "integration-lnd", "synced_to_chain": True, "block_height": "101"}
    if path == "/v1/balance/blockchain":
        return {"confirmed_balance": "100000"}
    if path == "/v1/balance/channels":
        return {"local_balance": {"sat": "60000"}, "remote_balance": {"sat": "40000"}}
    if path == "/v1/channels":
        return {"channels": [{"active": True, "remote_pubkey": "02" + "f" * 64, "capacity": "100000"}]}
    if path.startswith("/v1/invoice/"):
        payment_hash = path.rsplit("/", 1)[-1]
        return INVOICES.get(payment_hash, {"r_hash_str": payment_hash, "state": "OPEN", "settled": False})
    if path == "/v1/payments":
        payment_hash = query.get("payment_hash", [""])[0]
        return {"payments": [PAYMENTS[payment_hash]] if payment_hash in PAYMENTS else []}
    return {}


def route_post(path, body):
    if path == "/v1/invoices":
        invoice = {
            "r_hash": base64.b64encode(bytes.fromhex(PAYMENT_HASH)).decode(),
            "r_hash_str": PAYMENT_HASH,
            "payment_request": INVOICE,
            "value": body.get("value"),
            "state": "OPEN",
        }
        INVOICES[PAYMENT_HASH] = invoice
        return invoice
    if path == "/v1/channels/transactions":
        PAYMENTS[PAYMENT_HASH] = {"payment_hash": PAYMENT_HASH, "status": "SUCCEEDED", "value_sat": "2500", "fee_sat": "2"}
        return {"payment_hash": PAYMENT_HASH, "payment_preimage": "preimage"}
    return {}


ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
