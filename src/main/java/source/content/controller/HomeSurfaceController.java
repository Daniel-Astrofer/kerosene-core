package source.content.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.content.dto.HomeStageAckRequestDTO;
import source.content.dto.HomeSurfaceResponseDTO;
import source.content.service.HomeStageImpressionService;
import source.content.service.HomeSurfaceComposer;

@RestController
@RequestMapping("/content")
public class HomeSurfaceController {

    private final HomeSurfaceComposer homeSurfaceComposer;
    private final HomeStageImpressionService impressionService;

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
        Long userId = currentUserId();
        String resolvedLocale = firstNonBlank(locale, languageFromAccept(acceptLanguage), "pt");
        String resolvedTimeZone = firstNonBlank(timeZone, timeZoneHeader, "UTC");
        HomeSurfaceResponseDTO surface = homeSurfaceComposer.compose(
                userId, balanceView, resolvedLocale, resolvedTimeZone);
        return ResponseEntity.ok(ApiResponse.success("Home surface composed.", surface));
    }

    /**
     * Mark a Communication Stage piece as received/read so it is not re-shown (ONCE policy).
     */
    @PostMapping("/home-stage/ack")
    public ResponseEntity<ApiResponse<Void>> ackStage(@RequestBody HomeStageAckRequestDTO body) {
        Long userId = currentUserId();
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String languageFromAccept(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        String primary = acceptLanguage.split(",")[0].trim();
        int dash = primary.indexOf('-');
        if (dash > 0) {
            primary = primary.substring(0, dash);
        }
        int semi = primary.indexOf(';');
        if (semi > 0) {
            primary = primary.substring(0, semi);
        }
        return primary.isBlank() ? null : primary.toLowerCase();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
