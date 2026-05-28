package source.ledger.dto;

import java.math.BigDecimal;

public record InternalTransactionResponseDTO(
        String txid,
        String status,
        BigDecimal amount,
        String sender,
        String receiver,
        String context) {
}
