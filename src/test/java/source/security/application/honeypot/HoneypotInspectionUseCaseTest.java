package source.security.application.honeypot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.security.domain.honeypot.HoneypotInspectionOutcome;
import source.security.infra.honeypot.JacksonRequestJsonBodyParser;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoneypotInspectionUseCaseTest {

    private HoneypotInspectionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new HoneypotInspectionUseCase(new JacksonRequestJsonBodyParser(new ObjectMapper()));
    }

    @Test
    void inspect_ShouldAllow_WhenPayloadIsLegitimate() {
        var result = useCase.inspect(new HoneypotInspectionCommand(
                "POST",
                "/auth/login",
                "127.0.0.1",
                "JUnit",
                "{\"username\":\"human\"}".getBytes(StandardCharsets.UTF_8)));

        assertTrue(result.shouldContinueFilterChain());
        assertEquals(HoneypotInspectionOutcome.FORWARD, result.outcome());
    }

    @Test
    void inspect_ShouldRejectMalformedJson() {
        var result = useCase.inspect(new HoneypotInspectionCommand(
                "POST",
                "/auth/login",
                "127.0.0.1",
                "JUnit",
                "invalid-json{".getBytes(StandardCharsets.UTF_8)));

        assertEquals(HoneypotInspectionOutcome.REJECT, result.outcome());
        assertEquals(400, result.httpStatus());
    }

    @Test
    void inspect_ShouldBlackhole_WhenHoneypotFieldIsFilled() {
        var result = useCase.inspect(new HoneypotInspectionCommand(
                "POST",
                "/auth/login",
                "127.0.0.1",
                "JUnit",
                "{\"username\":\"bot\",\"__hp\":\"triggered\"}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(HoneypotInspectionOutcome.BLACKHOLE, result.outcome());
        assertEquals(200, result.httpStatus());
    }
}
