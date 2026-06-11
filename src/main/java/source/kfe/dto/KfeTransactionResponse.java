package source.kfe.dto;

import source.kfe.model.KfeDirection;
import source.kfe.model.KfeRail;
import source.kfe.model.KfeTransactionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record KfeTransactionResponse(
        UUID id,
        KfeTransactionStatus status,
        KfeRail rail,
        KfeDirection direction,
        UUID sourceWalletId,
        UUID destinationWalletId,
        long grossAmountSats,
        long receiverAmountSats,
        long networkFeeSats,
        long keroseneFeeSats,
        long totalDebitSats,
        String quorumProposalHash,
        int quorumAckCount,
        String providerReference,
        String blockchainTxid,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
