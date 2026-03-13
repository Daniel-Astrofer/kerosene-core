# Kerosene Hydra - OnionBalance Implementation Task List

- [x] Configure named volumes for Tor keys (`tor_keys_se`, `tor_keys_ee`, `tor_keys_is`)
- [x] Implement robust `onionbalance-entrypoint.sh` with:
    - [x] Automated requirements installation
    - [x] Backend hostname detection
    - [x] local Tor daemon management
    - [x] Python-based bootstrap and consensus check
- [x] Verify Backend instances connectivity (SE, EE, IS)
- [x] Monitor Master .onion address publication (Confirmed via DEBUG logs)
- [ ] Verify global accessibility of `sc3mol7ughlcsazgt2najfhgbjmwq74gmy4jclnkcjrwc4kc7shmzjad.onion`
- [ ] Plan Tor version upgrade (from 0.4.7.16 to 0.4.8+)
- [ ] Implement Tor Control Port authentication (security hardening)

## Vault STALL Issue Resolution
- [x] Synchronize container UIDs to 65532 for file permission consistency
- [x] Implement `Content-Length` parsing in `UdsSocks5Transport`
- [x] Implement read/write timeouts in `UdsSocks5Transport`
- [x] Enhance logging in `VaultKeyProvider` for better diagnostics
- [x] Implement HTTP Chunked Decoding in `UdsSocks5Transport`
- [x] Verify successful Vault provision and transition out of STALL mode
- [x] Fix `NullPointerException` in `SignupUseCase` finalization
- [x] Add lifecycle and state logging to `VaultController` for diagnostics
