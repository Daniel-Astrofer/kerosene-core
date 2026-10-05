package com.kerosene.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.kerosene.content.dto.HomeSurfaceResponseDTO;
import com.kerosene.content.model.entity.HomeUiOverrideEntity;
import com.kerosene.content.repository.HomeUiOverrideRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selects, orders, and applies targeted JSON overlays to the base home surface. */
@Service
public class HomeUiOverrideService {

    /** Logger for malformed overrides and fallback-to-default conditions. */
    private static final Logger log = LoggerFactory.getLogger(HomeUiOverrideService.class);

    /** Persistence boundary for active and saved overrides. */
    private final HomeUiOverrideRepository repository;
    /** Mapper used to parse patches and convert between JSON and typed surfaces. */
    private final ObjectMapper objectMapper;

    /** Creates the override service with its repository and mapper. */
    /** @param repository override persistence adapter @param objectMapper JSON parser and converter */
    public HomeUiOverrideService(HomeUiOverrideRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Applies matching active patches from lowest to highest priority so later
     * overlays win; invalid query or merged data falls back to the base surface.
     * @param base code-generated default surface
     * @param userId target account identifier
     * @param locale normalized locale used for audience matching
     * @param balanceView normalized balance mode used for audience matching
     * @return overlaid surface, or {@code base} when no valid overlays apply
     */
    public HomeSurfaceResponseDTO applyOverrides(
            HomeSurfaceResponseDTO base,
            Long userId,
            String locale,
            String balanceView) {
        List<String> segments = buildSegments(userId, locale, balanceView);
        List<HomeUiOverrideEntity> rows;
        try {
            rows = repository.findActiveMatching(userId, segments, Instant.now());
        } catch (Exception ex) {
            log.warn("home_ui_override query failed; using code defaults only: {}", ex.getMessage());
            return base;
        }
        if (rows == null || rows.isEmpty()) {
            return base;
        }

        ObjectNode tree = HomeSurfaceMerge.toObjectNode(objectMapper, base);
        // Walk ascending priority for correct overlay order.
        List<HomeUiOverrideEntity> ascending = new ArrayList<>(rows);
        ascending.sort((a, b) -> {
            int byPriority = Integer.compare(a.getPriority(), b.getPriority());
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(
                    a.getId() == null ? 0L : a.getId(),
                    b.getId() == null ? 0L : b.getId());
        });

        for (HomeUiOverrideEntity row : ascending) {
            JsonNode patch = parsePayload(row.getPayload());
            if (patch == null) {
                continue;
            }
            HomeSurfaceMerge.deepMerge(tree, patch);
        }
        try {
            return HomeSurfaceMerge.fromObjectNode(objectMapper, tree);
        } catch (Exception ex) {
            log.warn("home_ui_override merge produced invalid surface; using base: {}", ex.getMessage());
            return base;
        }
    }

    /** Persists an override entity through the configured repository. */
    /** @param entity override definition to save @return saved entity with persistence metadata */
    public HomeUiOverrideEntity save(HomeUiOverrideEntity entity) {
        return repository.save(entity);
    }

    /** Returns active override records matching the user's locale and balance segments. */
    /** @param userId target account identifier @param locale requested locale @param balanceView requested balance mode @return active matching override rows */
    public List<HomeUiOverrideEntity> findActiveForUser(Long userId, String locale, String balanceView) {
        return repository.findActiveMatching(userId, buildSegments(userId, locale, balanceView), Instant.now());
    }

    /** Parses only non-empty JSON objects; arrays, scalars, null, and malformed data are ignored. */
    private JsonNode parsePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ex) {
            log.warn("Invalid home_ui_override payload JSON: {}", ex.getMessage());
            return null;
        }
    }

    /** Builds stable audience selectors for locale, balance mode, and optional user bucket. */
    static List<String> buildSegments(Long userId, String locale, String balanceView) {
        List<String> segments = new ArrayList<>();
        String loc = locale == null || locale.isBlank() ? "pt" : locale.trim().toLowerCase(Locale.ROOT);
        String view = balanceView == null || balanceView.isBlank()
                ? "TOTAL"
                : balanceView.trim().toUpperCase(Locale.ROOT);
        segments.add("locale:" + loc);
        segments.add("balanceView:" + view);
        if (userId != null) {
            int bucket = (int) Math.floorMod(userId, 3L);
            segments.add("bucket:" + bucket);
        }
        return List.copyOf(segments);
    }
}
