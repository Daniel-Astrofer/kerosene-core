package source.security.infra.honeypot;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import source.security.domain.honeypot.HoneypotInspectionOutcome;
import source.security.domain.honeypot.HoneypotInspectionResult;

import java.io.IOException;
import java.time.Instant;

@Component
public class HoneypotHttpResponseWriter {

    public void write(HoneypotInspectionResult result, HttpServletResponse response) throws IOException {
        if (result.shouldContinueFilterChain()) {
            return;
        }

        response.setStatus(result.httpStatus());
        response.setContentType("application/json");
        response.getWriter().write(buildPayload(result));
    }

    private String buildPayload(HoneypotInspectionResult result) {
        boolean success = result.outcome() == HoneypotInspectionOutcome.BLACKHOLE;
        return "{\"success\":" + success
                + ",\"message\":\"" + result.clientMessage()
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
    }
}
