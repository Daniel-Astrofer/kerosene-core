package com.kerosene.notification.model.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class StringStringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize notification metadata", exception);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> parsed = MAPPER.readValue(dbData, TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (Exception exception) {
            return Collections.emptyMap();
        }
    }
}
