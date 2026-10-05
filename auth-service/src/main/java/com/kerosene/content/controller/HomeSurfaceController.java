package com.kerosene.content.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.content.dto.HomeStageAckRequestDTO;
import com.kerosene.content.dto.HomeSurfaceResponseDTO;
import com.kerosene.content.service.HomeStageImpressionService;
import com.kerosene.content.service.HomeSurfaceComposer;

/** HTTP endpoints for composing the home surface and acknowledging one-shot stages. */
@RestController
@RequestMapping("/content")
public class HomeSurfaceController {

    /** Application service that builds the full response surface. */
    private final HomeSurfaceComposer homeSurfaceComposer;
    /** Records authenticated client acknowledgements for one-shot content. */
    private final HomeStageImpressionService impressionService;

    /** Creates the controller with surface composition and acknowledgement services. */
    /** @param homeSurfaceComposer home response assembler @param impressionService stage acknowledgement service */
    public HomeSurfaceController(
            HomeSurfaceComposer homeSurfaceComposer,
            HomeStageImpressionService impressionService) {
        this.homeSurfaceComposer = homeSurfaceComposer;
        this.impressionService = impressionService;
    }

    /**
     * Full home surface composition: layout, header (greeting/actions), feed.
     */
    @GetMapping("/home-surface")
    public ResponseEntity<ApiResponse<HomeSurfaceResponseDTO>> homeSurface(
            @RequestParam(name = "balanceView", required = false, defaultValue = "TOTAL") String balanceView,
            @RequestParam(name = "locale", required = false, defaultValue = "pt") String locale,
            @RequestParam(name = "timeZone", required = false) String timeZone,
            @RequestHeader(name = "X-Timezone", required = false) String timeZoneHeader,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
        Long userId = HomeRequestSupport.currentUserId();
        String resolvedLocale = HomeRequestSupport.firstNonBlank(
                locale, HomeRequestSupport.languageFromAccept(acceptLanguage), "pt");
        String resolvedTimeZone = HomeRequestSupport.firstNonBlank(timeZone, timeZoneHeader, "UTC");
        HomeSurfaceResponseDTO surface = homeSurfaceComposer.compose(
                userId, balanceView, resolvedLocale, resolvedTimeZone);
        return ResponseEntity.ok(ApiResponse.success("Home surface composed.", surface));
    }

    /**
     * Mark a Communication Stage piece as received/read so it is not re-shown (ONCE policy).
     */
    @PostMapping("/home-stage/ack")
    public ResponseEntity<ApiResponse<Void>> ackStage(@RequestBody HomeStageAckRequestDTO body) {
        Long userId = HomeRequestSupport.currentUserId();
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Authentication required.", "UNAUTHORIZED"));
        }
        try {
            impressionService.acknowledge(userId, body);
            return ResponseEntity.ok(ApiResponse.success("Stage acknowledged.", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage(), "BAD_REQUEST"));
        }
    }

}
