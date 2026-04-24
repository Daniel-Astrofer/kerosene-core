package source.common.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RootStatusControllerTest {

    @Test
    void shouldExposePublicRootStatusPayload() {
        RootStatusController controller = new RootStatusController("v0.5");

        Map<String, Object> payload = controller.root();

        assertEquals("ok", payload.get("status"));
        assertEquals("v0.5", payload.get("service"));
        assertEquals("/actuator/health", payload.get("health"));
        assertEquals("/sovereignty/status", payload.get("sovereignty"));
    }
}
