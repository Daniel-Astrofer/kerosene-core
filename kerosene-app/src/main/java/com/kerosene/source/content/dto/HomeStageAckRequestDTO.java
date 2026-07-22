package source.content.dto;

/**
 * Client acknowledges that the user received/read a Communication Stage piece.
 *
 * <p>Prefer sending {@code contentFingerprint} from the same edition that was shown.
 * If omitted, server builds it from stageId + kind + title + body.
 */
public record HomeStageAckRequestDTO(
        String stageId,
        String kind,
        String title,
        String body,
        String contentFingerprint,
        /** SEEN | READ | DISMISSED — default READ */
        String status) {}
