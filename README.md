# Kerosene Hydra v5.0 — Backend Service

> **Hydra** is the core custody and consensus service for the Kerosene ecosystem. It manages Bitcoin assets through a distributed, sovereign architecture with hardware-backed security.

---

## 🏛️ Documentation Wiki

We maintain an **extremely robust** internal documentation suite for developers and auditors:

- 📑 **[API REFERENCE](API_REFERENCE.md)**: Exhaustive catalog of **45+ endpoints**, including DTO schemas, Honeypot defense, and Error Codes.
- 🏗️ **[ARCHITECTURE](ARCHITECTURE.md)**: Technical deep-dive into the Raft-like 2PC Quorum, TPM 2.0 Attestation, and Memory Protection (`mlock`).
- 🐋 **[INFRASTRUCTURE](INFRASTRUCTURE.md)**: Guide to Tor onion routing (UDS SOCKS5), Docker orchestration, and security hardening.
- 🤖 **[llms.txt](llms.txt)**: Context-optimized file for AI agents and automated indexing.

---

## 🚀 Quick Start (Development)

1.  **Dependencies**: Java 21+, Docker, and Tor.
2.  **Environment**: Copy `env.example` to `.env`.
3.  **Boot**:
    ```bash
    ./gradlew bootRun
    ```
4.  **Verification**:
    ```bash
    curl http://localhost:8080/sovereignty/ping
    ```

---

## 🛡️ Security Pillars

| Pillar | Mechanism | Status |
| :--- | :--- | :--- |
| **Consensus** | Raft-2PC Quorum (3 Nodes) | ACTIVE |
| **Privacy** | mTLS over Tor (Onion V3) | MANDATORY |
| **Identity** | TPM 2.0 Hardened Quote | ACTIVE |
| **Memory** | JVM Reflection Zeroing + `mlock` | ACTIVE |

---

## 👨‍💻 Contributing

Please read the **[ARCHITECTURE.md](ARCHITECTURE.md)** before modifying the Ledger or Quorum services. These components are extremely sensitive to timing and consistency.

---

**Kerosene Project** — *Privacy is Sovereignty.*
