package source.transactions.application.externalpayments;

public interface ExternalPaymentsCustodyPort {

    String providerName();

    PaymentResult sendOnchain(OnchainPaymentCommand command);

    record OnchainPaymentCommand(
            Long userId,
            Long walletId,
            String walletName,
            String destinationAddress,
            long amountSats,
            String description,
            String authorizationProof) {
    }

    record PaymentResult(
            String providerReference,
            String txid,
            String paymentHash,
            String status,
            long feeSats,
            String rawPayload) {
    }
}
