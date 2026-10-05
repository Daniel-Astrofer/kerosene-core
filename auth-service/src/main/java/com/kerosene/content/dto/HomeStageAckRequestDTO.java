package com.kerosene.content.dto;

/**
 * Client acknowledges that the user received/read a Communication Stage piece.
 *
 * <p>Prefer sending {@code contentFingerprint} from the same edition that was shown.
 * If omitted, server builds it from stageId + kind + title + body.
 * @param stageId identifier of the stage the client displayed
 * @param kind stage kind used to validate the acknowledgement
 * @param title displayed title used to derive a fingerprint when none is supplied
 * @param body displayed body used to derive a fingerprint when none is supplied
 * @param contentFingerprint fingerprint of the exact content edition rendered to the user
 * @param status acknowledgement state: SEEN, READ, or DISMISSED; defaults to READ
 */
public record HomeStageAckRequestDTO(
        String stageId,
        String kind,
        String title,
        String body,
        String contentFingerprint,
        String status) {}
