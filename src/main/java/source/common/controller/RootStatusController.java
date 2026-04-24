package source.common.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootStatusController {

    private final String applicationName;

    public RootStatusController(@Value("${spring.application.name:kerosene}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        return statusPayload();
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        return statusPayload();
    }

    private Map<String, Object> statusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("service", applicationName);
        payload.put("region", System.getenv().getOrDefault("REGION", "DEV"));
        payload.put("timestamp", Instant.now().toString());
        payload.put("health", "/actuator/health");
        payload.put("sovereignty", "/sovereignty/status");
        return payload;
    }
}
