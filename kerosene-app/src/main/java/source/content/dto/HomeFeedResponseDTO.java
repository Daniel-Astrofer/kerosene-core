package source.content.dto;

import java.util.List;

public record HomeFeedResponseDTO(
        String version,
        int ttlSeconds,
        String balanceView,
        String locale,
        String timeZone,
        List<HomeFeedItemDTO> items) {
}
