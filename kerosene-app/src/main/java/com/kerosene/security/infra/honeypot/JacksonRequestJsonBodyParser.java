package com.kerosene.security.infra.honeypot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.kerosene.security.application.honeypot.RequestJsonBodyParser;

import java.io.IOException;

@Component
public class JacksonRequestJsonBodyParser implements RequestJsonBodyParser {

    private final ObjectMapper objectMapper;

    public JacksonRequestJsonBodyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode parse(byte[] rawBody) throws IOException {
        return objectMapper.readTree(rawBody);
    }
}
