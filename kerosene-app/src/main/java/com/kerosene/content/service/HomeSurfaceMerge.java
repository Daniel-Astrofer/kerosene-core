package source.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import source.content.dto.HomeSurfaceResponseDTO;

/**
 * Deep-merge helpers for partial home-surface JSON overlays.
 */
final class HomeSurfaceMerge {

    private HomeSurfaceMerge() {
    }

    static ObjectNode toObjectNode(ObjectMapper mapper, HomeSurfaceResponseDTO surface) {
        return mapper.valueToTree(surface);
    }

    static HomeSurfaceResponseDTO fromObjectNode(ObjectMapper mapper, ObjectNode node) {
        return mapper.convertValue(node, HomeSurfaceResponseDTO.class);
    }

    /**
     * Deep-merge {@code patch} into {@code base}. Arrays and scalars in patch replace base.
     * Objects are merged recursively. Null patch fields are ignored.
     */
    static ObjectNode deepMerge(ObjectNode base, JsonNode patch) {
        if (patch == null || patch.isNull() || !patch.isObject()) {
            return base;
        }
        patch.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            JsonNode existing = base.get(key);
            if (value.isObject() && existing != null && existing.isObject()) {
                deepMerge((ObjectNode) existing, value);
            } else if (value.isArray()) {
                base.set(key, value.deepCopy());
            } else {
                base.set(key, value.deepCopy());
            }
        });
        return base;
    }

    static ObjectNode emptyObject(ObjectMapper mapper) {
        return mapper.createObjectNode();
    }

    static ArrayNode emptyArray(ObjectMapper mapper) {
        return mapper.createArrayNode();
    }
}
