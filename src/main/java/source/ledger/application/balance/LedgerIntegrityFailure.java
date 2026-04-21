package source.ledger.application.balance;

public record LedgerIntegrityFailure(
        Integer ledgerId,
        Long walletId,
        Long userId,
        String reason) {
}
