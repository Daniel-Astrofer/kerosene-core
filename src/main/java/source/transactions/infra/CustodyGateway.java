package source.transactions.infra;

import java.time.LocalDateTime;

public interface CustodyGateway {

    boolean isLive();

    String providerName();

    GeneratedOnchainAddress createOnchainAddress(OnchainAddressCommand command);

    GeneratedLightningInvoice createLightningInvoice(LightningInvoiceCommand command);

    PaymentResult sendOnchain(OnchainPaymentCommand command);

    PaymentResult payLightning(LightningPaymentCommand command);

    record OnchainAddressCommand(
            Long userId,
            Long walletId,
            String walletName,
            String label) {
    }

    record GeneratedOnchainAddress(
            String address,
            String walletReference,
            String providerReference) {
    }

    record LightningInvoiceCommand(
            Long userId,
            Long walletId,
            String walletName,
            long amountSats,
            String memo,
            int expiresInSeconds) {
    }

    record GeneratedLightningInvoice(
            String paymentRequest,
            String paymentHash,
            String lightningAddress,
            String providerReference,
            LocalDateTime expiresAt) {
    }

    record OnchainPaymentCommand(
            Long userId,
            Long walletId,
            String walletName,
            String destinationAddress,
            long amountSats,
            String description,
            String authorizationProof) {
    }

    record LightningPaymentCommand(
            Long userId,
            Long walletId,
            String walletName,
            String paymentRequest,
            long amountSats,
            long maxFeeSats,
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
