package com.kerosene.content.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.content.dto.HomeFeedResponseDTO;
import com.kerosene.content.service.HomeFeedComposer;

/** HTTP endpoint for the personalized content feed shown on the home screen. */
@RestController
@RequestMapping("/content")
public class HomeFeedController {

    /** Application service that selects and composes feed items. */
    private final HomeFeedComposer homeFeedComposer;

    /** Creates the endpoint with its feed composition service. */
    /** @param homeFeedComposer feed selection and rendering service */
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
        Long userId = HomeRequestSupport.currentUserId();
        String resolvedLocale = HomeRequestSupport.firstNonBlank(
                locale, HomeRequestSupport.languageFromAccept(acceptLanguage), "pt");
        String resolvedTimeZone = HomeRequestSupport.firstNonBlank(timeZone, timeZoneHeader, "UTC");
        HomeFeedResponseDTO feed = homeFeedComposer.compose(
                userId, balanceView, resolvedLocale, resolvedTimeZone);
        return ResponseEntity.ok(ApiResponse.success("Home feed composed.", feed));
    }

}
