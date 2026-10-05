package com.kerosene.auth.application.service.devicekey;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Produces the constrained deterministic JSON encoding signed by the device-key protocol. */
public final class DeviceKeyCanonicalJson {

    /** Prevents construction of this stateless canonicalization utility. */
    private DeviceKeyCanonicalJson() {
    }

    /** Canonicalizes fields and encodes the resulting text as UTF-8. */
    /** @param values simple string-keyed payload fields */
    /** @return canonical UTF-8 bytes */
    public static byte[] utf8Bytes(Map<String, ?> values) {
        return canonicalize(values).getBytes(StandardCharsets.UTF_8);
    }

    /** Sorts keys and renders only non-null strings, integral numbers, and booleans. */
    /** @param values protocol payload fields */
    /** @return deterministic compact JSON object */
    /** @throws IllegalArgumentException when keys or values are outside canonicalization v1 */
    public static String canonicalize(Map<String, ?> values) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Canonical JSON keys must be non-empty strings.");
            }
            if (value == null) {
                throw new IllegalArgumentException("Canonical JSON v1 does not allow null values.");
            }
            sorted.put(key, value);
        });

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(renderValue(entry.getValue()));
        }
        return json.append('}').toString();
    }

    /** Renders one supported primitive value without whitespace or locale-sensitive conversion. */
    /** @param value non-null string, integral wrapper, or boolean */
    /** @return JSON literal */
    private static String renderValue(Object value) {
        if (value instanceof String stringValue) {
            return quote(stringValue);
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue.toString();
        }
        throw new IllegalArgumentException("Canonical JSON v1 only supports string, integer, and boolean values.");
    }

    /** Escapes JSON string control characters and quotation marks. */
    /** @param value source text */
    /** @return quoted JSON string */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
