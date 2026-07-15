package source.content.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.content.dto.HomeFeedResponseDTO;
import source.content.service.HomeFeedComposer;

@RestController
@RequestMapping("/content")
public class HomeFeedController {

    private final HomeFeedComposer homeFeedComposer;

    public HomeFeedController(HomeFeedComposer homeFeedComposer) {
        this.homeFeedComposer = homeFeedComposer;
    }

    /**
     * Personalized home education / announcement / promo feed.
     * No CMS admin — composition is rule-based from a product-owned catalog.
     */
    @GetMapping("/home-feed")
    public ResponseEntity<ApiResponse<HomeFeedResponseDTO>> homeFeed(
            @RequestParam(name = "balanceView", required = false, defaultValue = "TOTAL") String balanceView,
            @RequestParam(name = "locale", required = false, defaultValue = "pt") String locale,
            @RequestParam(name = "timeZone", required = false) String timeZone,
            @org.springframework.web.bind.annotation.RequestHeader(
                    name = "X-Timezone",
                    required = false) String timeZoneHeader,
            @org.springframework.web.bind.annotation.RequestHeader(
                    name = "Accept-Language",
                    required = false) String acceptLanguage) {
        Long userId = currentUserId();
        String resolvedLocale = firstNonBlank(locale, languageFromAccept(acceptLanguage), "pt");
        String resolvedTimeZone = firstNonBlank(timeZone, timeZoneHeader, "UTC");
        HomeFeedResponseDTO feed = homeFeedComposer.compose(
                userId, balanceView, resolvedLocale, resolvedTimeZone);
        return ResponseEntity.ok(ApiResponse.success("Home feed composed.", feed));
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
