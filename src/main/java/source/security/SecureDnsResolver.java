package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Map;

/**
 * ─── DNS Pinning Guard (Issue 1.1) ──────────────────────────────────────────
 *
 * Problem: Docker's embedded DNS resolver (127.0.0.11) can be spoofed if the
 * Docker daemon is compromised — a malicious resolver could redirect Vault or
 * DB traffic to attacker-controlled IPs.
 *
 * Solution: Maintain a static map of expected IPs for every sensitive hostname.
 * Before any connection, resolve the hostname and verify it matches the pinned
 * record using constant-time comparison (MessageDigest.isEqual).
 *
 * Usage: Inject this service into VaultKeyProvider and QuorumSyncService
 * before establishing connections to sensitive endpoints.
 *
 * Important: IPs here are the EXPECTED Docker-network IPs as defined in
 * docker-compose.yml networks. Update these if subnets change.
 */
@Service
public class SecureDnsResolver {

    private static final Logger log = LoggerFactory.getLogger(SecureDnsResolver.class);

    /**
     * Static pinned records — hostname → expected IP within the Docker network.
     * `.onion` addresses are not resolved by local DNS (they route via Tor),
     * so they map to 127.0.0.1 as a sentinel to skip local resolution check.
     */
    private static final Map<String, String> PINNED = Map.ofEntries(
            Map.entry("kerosene_db_is", "172.20.1.10"),
            Map.entry("kerosene_db_ch", "172.20.2.10"),
            Map.entry("kerosene_db_sg", "172.20.3.10"),
            Map.entry("kerosene-tor-is", "172.20.1.50"),
            Map.entry("kerosene-tor-ch", "172.20.2.50"),
            Map.entry("kerosene-tor-sg", "172.20.3.50"),
            Map.entry("kerosene_redis_is", "172.20.1.30"),
            Map.entry("kerosene_redis_ch", "172.20.2.30"),
            Map.entry("kerosene_redis_sg", "172.20.3.30"),
            Map.entry("mpc-sidecar-is", "172.20.1.70"),
            // .onion addresses bypass local DNS — they resolve inside Tor circuit.
            // We pin them to 127.0.0.1 as a sentinel to skip local resolution check.
            Map.entry("kvaultv3xsc3mol7ughlcsazgt2najfhgbjmwq74gmy4jclnkcjrwc4.onion", "127.0.0.1"));

    /**
     * Resolves a hostname and verifies it against the pinned record.
     *
     * @param hostname the Docker-internal hostname to resolve
     * @throws SecurityException if hostname is not in the pinned list,
     *                           or if the resolved IP does not match
     */
    public void verifyAndPin(String hostname) {
        String expected = PINNED.get(hostname);
        if (expected == null) {
            log.error("[DNS-PINNING] Hostname '{}' is NOT in the pinned allowlist. Connection blocked.", hostname);
            throw new SecurityException("DNS pinning violation: unknown hostname '" + hostname + "'");
        }

        // .onion addresses route via Tor — skip local resolution
        if ("127.0.0.1".equals(expected)) {
            log.debug("[DNS-PINNING] Skipping local resolution for .onion hostname: {}", hostname);
            return;
        }

        try {
            InetAddress[] resolved = InetAddress.getAllByName(hostname);
            for (InetAddress addr : resolved) {
                String actual = addr.getHostAddress();
                // Constant-time comparison prevents timing oracle on the IP check
                if (!MessageDigest.isEqual(
                        actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    log.error("[DNS-SPOOFING DETECTED] {} resolved to {} but pinned record is {}. BLOCKING.",
                            hostname, actual, expected);
                    throw new SecurityException(
                            "DNS spoofing detected: '" + hostname + "' resolved to unexpected IP '" + actual + "'");
                }
            }
            log.debug("[DNS-PINNING] {} verified → {}", hostname, expected);
        } catch (UnknownHostException e) {
            log.error("[DNS-PINNING] Resolution failed for hostname '{}': {}", hostname, e.getMessage());
            throw new SecurityException("DNS resolution failed for '" + hostname + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns the pinned IP for a hostname without performing live DNS resolution.
     * Useful when the JVM should connect directly by IP to avoid any resolver.
     */
    public String getPinnedIp(String hostname) {
        String pinned = PINNED.get(hostname);
        if (pinned == null) {
            throw new SecurityException("Hostname not in DNS pinned list: '" + hostname + "'");
        }
        return pinned;
    }

    /**
     * Returns true if the hostname is in the pinned allowlist (regardless of IP).
     */
    public boolean isAllowed(String hostname) {
        return PINNED.containsKey(hostname);
    }
}
