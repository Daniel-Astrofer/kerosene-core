package source.common.infra.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

@Component
public class TorHealthIndicator implements HealthIndicator {

    // Usually, the application connects to a local Tor proxy at port 9050
    private static final String SOCKS_PROXY_HOST = "127.0.0.1";
    private static final int SOCKS_PROXY_PORT = 9050; // Adjust for your docker setup if needed

    @Override
    public Health health() {
        if (checkTorConnection()) {
            return Health.up().withDetail("tor_proxy", "Connection successful")
                    .withDetail("host", SOCKS_PROXY_HOST)
                    .withDetail("port", SOCKS_PROXY_PORT)
                    .build();
        }
        return Health.down().withDetail("tor_proxy", "Connection failed").build();
    }

    private boolean checkTorConnection() {
        try (Socket socket = new Socket()) {
            // Attempt an immediate connection to the local Tor SOCKS proxy instance
            socket.connect(new InetSocketAddress(SOCKS_PROXY_HOST, SOCKS_PROXY_PORT), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
