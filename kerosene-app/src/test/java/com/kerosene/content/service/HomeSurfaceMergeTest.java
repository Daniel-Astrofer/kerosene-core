package com.kerosene.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import com.kerosene.content.dto.HomeActionVisibilityDTO;
import com.kerosene.content.dto.HomeFeedSurfaceDTO;
import com.kerosene.content.dto.HomeGreetingDTO;
import com.kerosene.content.dto.HomeGreetingFallbackDTO;
import com.kerosene.content.dto.HomeGreetingPresentationDTO;
import com.kerosene.content.dto.HomeGreetingRotationDTO;
import com.kerosene.content.dto.HomeHeaderActionsDTO;
import com.kerosene.content.dto.HomeHeaderDTO;
import com.kerosene.content.dto.HomeHeaderSpacingDTO;
import com.kerosene.content.dto.HomeLayoutDTO;
import com.kerosene.content.dto.HomeRestingHeaderDTO;
import com.kerosene.content.dto.HomeStageDTO;
import com.kerosene.content.dto.HomeStyleTokensDTO;
import com.kerosene.content.dto.HomeSurfaceResponseDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HomeSurfaceMergeTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deepMergeCanPatchStageBodyShift() throws Exception {
        ObjectNode tree = HomeSurfaceMerge.toObjectNode(mapper, sampleSurface());
        ObjectNode patch = mapper.readValue(
                """
                {
                  "stage": {
                    "motion": {
                      "bodyShift": { "offsetPx": 48, "durationMs": 600, "curve": "EASE_IN_OUT" }
                    },
                    "layout": {
                      "actions": { "placement": "BELOW_STAGE", "policy": "ALWAYS_VISIBLE" }
                    }
                  }
                }
                """,
                ObjectNode.class);
        HomeSurfaceMerge.deepMerge(tree, patch);
        HomeSurfaceResponseDTO merged = HomeSurfaceMerge.fromObjectNode(mapper, tree);

        assertEquals(48, merged.stage().motion().bodyShift().offsetPx());
        assertEquals(600, merged.stage().motion().bodyShift().durationMs());
        assertEquals("EASE_IN_OUT", merged.stage().motion().bodyShift().curve());
        assertEquals("BELOW_STAGE", merged.stage().layout().actions().placement());
        assertFalse(Boolean.TRUE.equals(merged.stage().motion().bodyShift().fadeBody()));
    }

    private static HomeSurfaceResponseDTO sampleSurface() {
        return new HomeSurfaceResponseDTO(
                2,
                "v2",
                120,
                "TOTAL",
                "pt",
                "UTC",
                new HomeLayoutDTO(18, 18, 24, null),
                new HomeHeaderDTO(
                        new HomeGreetingDTO(
                                "STATIC",
                                new HomeGreetingFallbackDTO("TIME_OF_DAY", true),
                                List.of(),
                                new HomeGreetingRotationDTO(5000, false),
                                new HomeStyleTokensDTO("white", "w300"),
                                new HomeGreetingPresentationDTO(
                                        "ONCE", false, true, true, 36, true)),
                        new HomeHeaderActionsDTO(
                                new HomeActionVisibilityDTO(true),
                                new HomeActionVisibilityDTO(true),
                                new HomeActionVisibilityDTO(true)),
                        new HomeHeaderSpacingDTO(8, 12)),
                new HomeFeedSurfaceDTO("regular", null, 18, 8, "NONE", List.of()),
                HomeStageDTO.idle(),
                HomeRestingHeaderDTO.defaults());
    }

    @Test
    void deepMergeCanAddMultipleGlows() throws Exception {
        ObjectNode tree = HomeSurfaceMerge.toObjectNode(mapper, sampleSurface());
        ObjectNode patch = mapper.readValue(
                """
                {
                  "stage": {
                    "atmosphere": {
                      "glows": [
                        { "id": "a", "colorToken": "positive", "x": 0.3, "y": 0.0, "width": 1.2, "height": 0.4, "intensity": 0.5, "radius": 0.7, "zIndex": 0 },
                        { "id": "b", "colorToken": "amber", "x": 0.8, "y": 0.1, "width": 0.8, "height": 0.3, "intensity": 0.25, "radius": 0.6, "zIndex": 1 },
                        { "id": "c", "colorToken": "cold", "x": 0.1, "y": 0.2, "width": 0.6, "height": 0.25, "intensity": 0.18, "radius": 0.55, "zIndex": 2 }
                      ],
                      "animated": true,
                      "transitionMs": 500
                    }
                  }
                }
                """,
                ObjectNode.class);
        HomeSurfaceMerge.deepMerge(tree, patch);
        HomeSurfaceResponseDTO merged = HomeSurfaceMerge.fromObjectNode(mapper, tree);
        assertEquals(3, merged.stage().atmosphere().glows().size());
        assertEquals("positive", merged.stage().atmosphere().glows().get(0).colorToken());
        assertEquals(0.8, merged.stage().atmosphere().glows().get(1).x());
    }
}
