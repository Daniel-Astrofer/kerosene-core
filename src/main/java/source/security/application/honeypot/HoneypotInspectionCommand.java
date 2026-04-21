package source.security.application.honeypot;

import java.util.Arrays;

public record HoneypotInspectionCommand(
        String method,
        String path,
        String remoteAddress,
        String userAgent,
        byte[] body) {

    public HoneypotInspectionCommand {
        body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }
}
