package source.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.content.dto.HomeStageAckRequestDTO;
import source.content.dto.HomeStageDTO;
import source.content.dto.HomeSurfaceResponseDTO;
import source.content.model.entity.HomeStageImpressionEntity;
import source.content.repository.HomeStageImpressionRepository;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Tracks which stage content editions a user has already consumed so ONCE
 * theater pieces are not re-shown until the content changes.
 */
@Service
public class HomeStageImpressionService {

    private static final Logger log = LoggerFactory.getLogger(HomeStageImpressionService.class);

    private static final Set<String> VALID_STATUS = Set.of("SEEN", "READ", "DISMISSED");

    private final HomeStageImpressionRepository repository;

    public HomeStageImpressionService(HomeStageImpressionRepository repository) {
        this.repository = repository;
    }

    /**
     * If the composed stage is ONCE and the user already read this fingerprint,
     * replace with idle so the client shows the resting header.
     */
    public HomeSurfaceResponseDTO suppressIfAlreadyRead(Long userId, HomeSurfaceResponseDTO surface) {
        if (userId == null || surface == null || surface.stage() == null) {
            return surface;
        }
        HomeStageDTO stage = surface.stage();
        if (!isOnceActive(stage)) {
            return surface;
        }
        String fp = HomeStageFingerprint.of(stage);
        if (!hasActiveImpression(userId, fp)) {
            return surface;
        }
        log.debug("Suppressing stage {} for user {} (fingerprint {})", stage.id(), userId, fp);
        return withIdleStage(surface);
    }

    public boolean hasActiveImpression(Long userId, String fingerprint) {
        if (userId == null || fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        return repository.existsActiveImpression(userId, fingerprint, Instant.now());
    }

    @Transactional
    public void acknowledge(Long userId, HomeStageAckRequestDTO request) {
        if (userId == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Body required");
        }
        String stageId = trimToNull(request.stageId());
        if (stageId == null) {
            throw new IllegalArgumentException("stageId is required");
        }
        if ("idle".equalsIgnoreCase(stageId) || "IDLE".equalsIgnoreCase(nullToEmpty(request.kind()))) {
            return; // nothing to record
        }

        String fingerprint = trimToNull(request.contentFingerprint());
        if (fingerprint == null) {
            fingerprint = HomeStageFingerprint.ofParts(
                    stageId,
                    nullToEmpty(request.kind()),
                    nullToEmpty(request.title()),
                    nullToEmpty(request.body()));
        }

        String status = normalizeStatus(request.status());

        var existing = repository.findByUserIdAndContentFingerprint(userId, fingerprint);
        if (existing.isPresent()) {
            HomeStageImpressionEntity row = existing.get();
            // Upgrade SEEN → READ/DISMISSED if needed; never erase.
            if (rank(status) > rank(row.getStatus())) {
                row.setStatus(status);
                row.setSeenAt(Instant.now());
                repository.save(row);
            }
            return;
        }

        HomeStageImpressionEntity row = new HomeStageImpressionEntity();
        row.setUserId(userId);
        row.setStageId(stageId.length() > 128 ? stageId.substring(0, 128) : stageId);
        row.setContentFingerprint(fingerprint.length() > 64 ? fingerprint.substring(0, 64) : fingerprint);
        row.setStatus(status);
        row.setSeenAt(Instant.now());
        repository.save(row);
        log.info("Recorded home stage {} for user {} status={}", stageId, userId, status);
    }

    private static boolean isOnceActive(HomeStageDTO stage) {
        if (stage == null || stage.kind() == null) {
            return false;
        }
        if ("IDLE".equalsIgnoreCase(stage.kind())) {
            return false;
        }
        String policy = stage.playPolicy() == null ? "ONCE" : stage.playPolicy().trim().toUpperCase(Locale.ROOT);
        // LOOP / PINNED intentionally re-show; only ONCE is one-shot content.
        return "ONCE".equals(policy);
    }

    private static HomeSurfaceResponseDTO withIdleStage(HomeSurfaceResponseDTO surface) {
        return new HomeSurfaceResponseDTO(
                surface.schemaVersion(),
                surface.version(),
                surface.ttlSeconds(),
                surface.balanceView(),
                surface.locale(),
                surface.timeZone(),
                surface.layout(),
                surface.header(),
                surface.feed(),
                HomeStageDTO.idle(),
                surface.restingHeader());
    }

    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "READ";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return VALID_STATUS.contains(s) ? s : "READ";
    }

    private static int rank(String status) {
        return switch (normalizeStatus(status)) {
            case "SEEN" -> 1;
            case "READ" -> 2;
            case "DISMISSED" -> 3;
            default -> 0;
        };
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
