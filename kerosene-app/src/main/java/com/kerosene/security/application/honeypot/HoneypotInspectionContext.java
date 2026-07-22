package source.security.application.honeypot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;

public class HoneypotInspectionContext {

    private final HoneypotInspectionCommand command;
    private final byte[] body;
    private JsonNode parsedBody;

    public HoneypotInspectionContext(HoneypotInspectionCommand command) {
        this.command = command;
        this.body = command.body();
    }

    public boolean hasBody() {
        return body.length > 0;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public String path() {
        return command.path();
    }

    public String remoteAddress() {
        return command.remoteAddress();
    }

    public String userAgent() {
        return command.userAgent();
    }

    public JsonNode parsedBody() {
        return parsedBody;
    }

    public void setParsedBody(JsonNode parsedBody) {
        this.parsedBody = parsedBody;
    }
}
