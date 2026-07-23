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

@Service
public class HomeUiOverrideService {

    private static final Logger log = LoggerFactory.getLogger(HomeUiOverrideService.class);

    private final HomeUiOverrideRepository repository;
    private final ObjectMapper objectMapper;

    public HomeUiOverrideService(HomeUiOverrideRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

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
        for (HomeUiOverrideEntity row : rows) {
            // Apply lowest priority first so higher priority wins (list is DESC).
        }
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

    public HomeUiOverrideEntity save(HomeUiOverrideEntity entity) {
        return repository.save(entity);
    }

    public List<HomeUiOverrideEntity> findActiveForUser(Long userId, String locale, String balanceView) {
        return repository.findActiveMatching(userId, buildSegments(userId, locale, balanceView), Instant.now());
    }

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
