# Hydra Infrastructure v5.0

Deployment orchestration, network isolation, and cold-storage management for the Kerosene Hydra backend.

---

## 1. Container Orchestration & Topology

Hydra is deployed as a sovereign cluster of Docker services, strictly isolated from the public internet.

### Network Architecture
- **Inbound**: All traffic flows through the `tor-gateway` container. No ports are exposed to the host machine except the Tor SOCKS/Control ports (if configured).
- **Consensus (mTLS)**: Shards communicate over a dedicated `quorum-net` overlay network.
- **Data Persistence**: Volumes are encrypted using `dm-crypt/LUKS` at the host level.

### Service Stack (`docker-compose.yml`)

```yaml
services:
  hydra-shard:
    image: kerosene/hydra:v5.0
    volumes:
      - /var/run/tor/socks:/var/run/tor/socks:ro # UDS SOCKS Proxy
      - /dev/tpmrm0:/dev/tpmrm0 # TPM 2.0 Access
    environment:
      - VAULT_URL=http://kerosene-vault.onion
      - QUORUM_SHARD_URLS=is.onion,sg.onion,ch.onion
    deploy:
      resources:
        limits:
          memory: 4G # mlock requires sufficient headroom

  tor-gateway:
    image: kerosene/tor-node
    cap_add:
      - NET_ADMIN # Required for transparent proxying
```

---

## 2. Tor Integration (Zero-TCP)

Hydra avoids the standard TCP networking stack for onion routing to prevent DNS leaks and fingerprinting.

### UDS SOCKS5 Proxy
The application uses a **Unix Domain Socket (UDS)** located at `/var/run/tor/socks/tor.sock` to communicate with the Tor gateway. 
- **Benefit**: Zero TCP overhead for local proxying.
- **Prevention**: Eliminates the possibility of local DNS resolution leaking onion hostnames.

---

## 3. Hardening Protocols

### Runtime Environment
- **JDK 21 Hardening**: JVM flags `-XX:+AlwaysLockClassLoader` and `-XX:+RestrictContigousMem` are enabled.
- **Process Isolation**: The application runs as a non-root `kerosene` user with `no-new-privileges` capability.

### Storage Security
- **PostgreSQL**: Configured with `ssl_mode=verify-full` even for internal traffic.
- **Redis**: Persistence is DISABLED. All Redis data is volatile and exists only in RAM to minimize the forensic footprint of the session cache.

---

## 4. Disaster Recovery & Node Resurrection

If a node is "killed" by the `RemoteAttestationService` (hardware violation):

1. **Shutdown**: Node enters ritual suicide, zeroing RAM.
2. **Investigation**: Operator must inspect the TPM PCR logs for the violation cause.
3. **Resurrection**:
   ```bash
   ./scripts/node-resurrect.sh --admin-token [VAULT_TOKEN]
   ```
4. **Resync**: The node re-authenticates with the Vault, fetches the Merkle Root from the majority, and rebuilds its local ledger state.
