package com.kerosene.content.service;

import org.springframework.stereotype.Service;
import com.kerosene.content.dto.HomeActionVisibilityDTO;
import com.kerosene.content.dto.HomeFeedResponseDTO;
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

import java.time.Instant;
import java.util.List;

/**
 * Envelope: Communication Stage (v2) + resting header + feed + layout.
 * Keeps legacy header.greeting fields filled for older clients (adapter).
 */
@Service
public class HomeSurfaceComposer {

    public static final int SCHEMA_VERSION = 2;
    private static final int TTL_SECONDS = 120;

    private final HomeFeedComposer homeFeedComposer;
    private final HomeStageComposer homeStageComposer;
    private final HomeUiOverrideService overrideService;
    private final HomeStageImpressionService impressionService;

    public HomeSurfaceComposer(
            HomeFeedComposer homeFeedComposer,
            HomeStageComposer homeStageComposer,
            HomeUiOverrideService overrideService,
            HomeStageImpressionService impressionService) {
        this.homeFeedComposer = homeFeedComposer;
        this.homeStageComposer = homeStageComposer;
        this.overrideService = overrideService;
        this.impressionService = impressionService;
    }

    public HomeSurfaceResponseDTO compose(
            Long userId,
            String balanceViewRaw,
            String localeRaw,
            String timeZoneRaw) {
        HomeFeedResponseDTO feed = homeFeedComposer.compose(userId, balanceViewRaw, localeRaw, timeZoneRaw);
        HomeSurfaceResponseDTO base = build(feed);
        HomeSurfaceResponseDTO withOverrides =
                overrideService.applyOverrides(base, userId, feed.locale(), feed.balanceView());
        // Drop ONCE stage editions the user already read (same content fingerprint).
        return impressionService.suppressIfAlreadyRead(userId, withOverrides);
    }

    private HomeSurfaceResponseDTO build(HomeFeedResponseDTO feed) {
        HomeStageDTO stage = homeStageComposer.compose(feed.locale(), feed.balanceView());
        HomeRestingHeaderDTO resting = homeStageComposer.restingHeader();
        HomeHeaderDTO legacyHeader = legacyHeaderFromStage(stage);

        return new HomeSurfaceResponseDTO(
                SCHEMA_VERSION,
                Instant.now().toString(),
                TTL_SECONDS,
                feed.balanceView(),
                feed.locale(),
                feed.timeZone(),
                new HomeLayoutDTO(18, 18, 24, null),
                legacyHeader,
                new HomeFeedSurfaceDTO(
                        "regular",
                        null,
                        18,
                        8,
                        "NONE",
                        feed.items() == null ? List.of() : feed.items()),
                stage,
                resting);
    }

    /** Back-compat for clients still reading header.greeting. */
    private static HomeHeaderDTO legacyHeaderFromStage(HomeStageDTO stage) {
        boolean active = stage != null && stage.kind() != null && !"IDLE".equalsIgnoreCase(stage.kind());
        if (!active) {
            return new HomeHeaderDTO(
                    new HomeGreetingDTO(
                            "STATIC",
                            new HomeGreetingFallbackDTO("TIME_OF_DAY", true),
                            List.of(),
                            new HomeGreetingRotationDTO(5000, false),
                            new HomeStyleTokensDTO("white", "w300"),
                            new HomeGreetingPresentationDTO(
                                    "ONCE", false, true, false, 0, false)),
                    new HomeHeaderActionsDTO(
                            new HomeActionVisibilityDTO(true),
                            new HomeActionVisibilityDTO(true),
                            new HomeActionVisibilityDTO(true)),
                    new HomeHeaderSpacingDTO(8, 12));
        }
        String title = stage.content() != null ? stage.content().title() : "";
        int duration = stage.lifecycle() != null && stage.lifecycle().showDurationMs() != null
                ? stage.lifecycle().showDurationMs()
                : 9000;
        int offset = stage.motion() != null
                && stage.motion().bodyShift() != null
                && stage.motion().bodyShift().offsetPx() != null
                ? stage.motion().bodyShift().offsetPx()
                : 36;
        return new HomeHeaderDTO(
                new HomeGreetingDTO(
                        "EPHEMERAL",
                        new HomeGreetingFallbackDTO("TIME_OF_DAY", true),
                        List.of(new com.kerosene.content.dto.HomeGreetingMessageDTO(
                                stage.id(),
                                title,
                                duration,
                                stage.priority(),
                                "MARQUEE",
                                new HomeStyleTokensDTO("white", "w300"),
                                null)),
                        new HomeGreetingRotationDTO(duration, false),
                        new HomeStyleTokensDTO("white", "w300"),
                        new HomeGreetingPresentationDTO(
                                "ONCE", false, true, true, offset, true)),
                new HomeHeaderActionsDTO(
                        new HomeActionVisibilityDTO(true),
                        new HomeActionVisibilityDTO(true),
                        new HomeActionVisibilityDTO(true)),
                new HomeHeaderSpacingDTO(8, 12));
    }
}
