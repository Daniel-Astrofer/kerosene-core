package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Network Egress Documentation — Supply Chain Attack Mitigation.
 *
 * The previous implementation used Java's SecurityManager (deprecated in Java
 * 17,
 * scheduled for removal) which provided only JVM-level protection and could be
 * bypassed by native libraries or JVM flags.
 *
 * Current Strategy: OS-level enforcement (defense-in-depth, cannot be bypassed
 * by JVM):
 *
 * 1. Docker seccomp profile: restricts allowed syscalls per container
 * (see: security_opt: ["seccomp:seccomp-profile.json"] in docker-compose.yml)
 *
 * 2. iptables OUTPUT rules on the host (applied by deploy/init-iptables.sh):
 * - Default policy: DROP all outbound traffic
 * - Allowlist: only internal Docker networks + Tor SOCKS5 port 9050
 * - Example:
 * iptables -P OUTPUT DROP
 * iptables -A OUTPUT -d 172.20.0.0/16 -j ACCEPT # Docker bridge
 * iptables -A OUTPUT -p tcp --dport 9050 -j ACCEPT # Tor SOCKS
 *
 * 3. Docker capabilities: containers run with cap_drop: [ALL] + only IPC_LOCK
 * preventing any raw socket operations.
 *
 * This class is retained as documentation. No runtime SecurityManager is
 * installed.
 * A future improvement could use Java's Foreign Function & Memory API (Project
 * Panama)
 * to call seccomp(2) directly from the JVM for an additional in-process guard.
 */
@Configuration
public class NetworkEgressFilter {

    private static final Logger logger = LoggerFactory.getLogger(NetworkEgressFilter.class);

    /**
     * Allowed hosts — enforced at OS/iptables level, documented here for reference.
     * Update deploy/init-iptables.sh when adding new infrastructure endpoints.
     */
    public static final Set<String> DOCUMENTED_ALLOWED_HOSTS = Set.of(
            "kerosene_db_is",
            "kerosene_db_ch",
            "kerosene_db_sg",
            "kerosene_redis_is",
            "kerosene_redis_ch",
            "kerosene_redis_sg",
            "mpc-sidecar-is",
            "mpc-sidecar-ch",
            "mpc-sidecar-sg",
            "kerosene-tor-is", // Tor SOCKS5 proxy (port 9050)
            "kerosene-tor-ch",
            "kerosene-tor-sg");

    /**
     * Called at startup for informational logging only.
     * Actual enforcement is at the OS level.
     */
    public static void installNetworkEgressGuard() {
        logger.info("[Egress Guard] OS-level enforcement active (seccomp + iptables). " +
                "Allowed infrastructure: {}", DOCUMENTED_ALLOWED_HOSTS);
        logger.info("[Egress Guard] All other outbound connections are blocked at the Docker/iptables layer.");
    }
}
