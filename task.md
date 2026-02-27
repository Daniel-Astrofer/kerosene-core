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
