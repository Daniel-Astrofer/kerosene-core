package com.kerosene.common.infra.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TorHealthIndicator implements HealthIndicator {

    private final Path socksPath;
    private final Path vanguardsStatePath;
    private final boolean required;

    public TorHealthIndicator(
            @Value("${tor.health.socks-path:/var/run/tor/socks/tor.sock}") String socksPath,
            @Value("${tor.health.vanguards-state-file:}") String vanguardsStatePath,
            @Value("${tor.health.required:true}") boolean required) {
        this.socksPath = Path.of(socksPath);
        this.vanguardsStatePath = vanguardsStatePath == null || vanguardsStatePath.isBlank()
                ? null
                : Path.of(vanguardsStatePath);
        this.required = required;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("required", required);

        // Local-full / onion-gateway-only: Tor runs in a separate pod; SOCKS is not
        // mounted into server/KFE. Actuator readiness includes this indicator, so when
        // tor is not required we must not report DOWN and fail /actuator/health.
        if (!required) {
            details.put("reason", "Tor health is not required for this profile");
            Health.Builder builder = Health.up();
            details.forEach(builder::withDetail);
            return builder.build();
        }

        boolean torSocketReady = Files.exists(socksPath) && Files.isReadable(socksPath);
        details.put("torSocksPath", socksPath.toString());
        details.put("torSocksReady", torSocketReady);

        boolean vanguardsReady = true;
        if (vanguardsStatePath != null) {
            vanguardsReady = Files.isRegularFile(vanguardsStatePath) && Files.isReadable(vanguardsStatePath);
            details.put("vanguardsStateFile", vanguardsStatePath.toString());
            details.put("vanguardsStateReady", vanguardsReady);
            if (vanguardsReady) {
                details.put("vanguardsStateSizeBytes", readFileSize(vanguardsStatePath));
                details.put("vanguardsStateLastModified", readLastModified(vanguardsStatePath));
            }
        }

        Health.Builder builder = torSocketReady && vanguardsReady ? Health.up() : Health.down();
        details.forEach(builder::withDetail);
        if (!torSocketReady) {
            builder.withDetail("reason", "Tor SOCKS Unix socket missing or unreadable");
        }
        if (torSocketReady && !vanguardsReady) {
            builder.withDetail("reason", "Tor is up but Vanguards state is missing or unreadable");
        }
        return builder.build();
    }

    private static long readFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return -1L;
        }
    }

    private static String readLastModified(Path path) {
        try {
            FileTime fileTime = Files.getLastModifiedTime(path);
            return fileTime.toString();
        } catch (IOException ignored) {
            return "unknown";
        }
    }
}
