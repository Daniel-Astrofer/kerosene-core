package source.security.application.honeypot;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public interface RequestJsonBodyParser {

    JsonNode parse(byte[] rawBody) throws IOException;
}
