package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.idempotency.IdempotencyKeyBuilder;
import source.common.validation.FinancialAmountValidator;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.ProcessedTransactionService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SendOnchainPaymentUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final ExternalPaymentsAuthorizationPort authorizationPort;
    private final ExternalPaymentsCustodyPort custodyPort;
    private final ExternalPaymentsFeePolicy feePolicy;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final ProcessedTransactionService processedTransactionService;
    private final source.transactions.service.ExternalProviderOutboxService outboxService;
    private final String localAddressProviderName;

    public SendOnchainPaymentUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsNotificationPort notificationPort,
            ExternalPaymentsAuthorizationPort authorizationPort,
            @Qualifier("bitcoinCorePsbtExternalPaymentsCustodyPort")
            ExternalPaymentsCustodyPort custodyPort,
            ExternalPaymentsFeePolicy feePolicy,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            ProcessedTransactionService processedTransactionService,
            source.transactions.service.ExternalProviderOutboxService outboxService,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.ledgerPort = ledgerPort;
        this.externalTransfersPort = externalTransfersPort;
        this.notificationPort = notificationPort;
        this.authorizationPort = authorizationPort;
        this.custodyPort = custodyPort;
        this.feePolicy = feePolicy;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.processedTransactionService = processedTransactionService;
        this.outboxService = outboxService;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public ExternalTransferResponseDTO send(Long userId, OnchainSendRequestDTO request) {
        requireIdempotencyKey(request.idempotencyKey());
        String idempotencyRef = IdempotencyKeyBuilder.build(
                "external-onchain-send",
                String.valueOf(userId),
                request.idempotencyKey());
        AtomicReference<ExternalTransferResponseDTO> response = new AtomicReference<>();
        boolean processed = processedTransactionService.processOnce(
                idempotencyRef,
                "EXTERNAL_ONCHAIN_SEND",
                () -> response.set(sendOnce(userId, request, idempotencyRef)));
        if (!processed) {
            throw new ExternalPaymentsExceptions.DuplicateExternalPayment(
                    "On-chain payment already submitted for this idempotency key.");
        }
        return response.get();
    }

    private ExternalTransferResponseDTO sendOnce(Long userId, OnchainSendRequestDTO request, String idempotencyRef) {
        FinancialAmountValidator.requirePositiveBtc(request.amount(), "amount");
        externalPaymentsMath.validatePositiveAmount(request.amount(), "On-chain payment amount must be positive.");
        if (!externalPaymentsMath.isValidBitcoinAddress(request.toAddress())) {
            throw new ExternalPaymentsExceptions.InvalidNetworkAddress(
                    "The destination Bitcoin address is invalid for the configured "
                            + externalPaymentsMath.configuredBitcoinNetwork()
                            + " network.");
        }

        WalletEntity wallet = walletPort.requireWallet(userId, request.fromWalletName());
        if (wallet.isSelfCustodyMode()) {
            throw new IllegalStateException(
                    "Self-custody wallets are monitored only. On-chain spends must be signed outside the platform.");
        }
        ExternalPaymentsAuthorizationPort.AuthorizationResult authorization = authorizationPort.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        long maxFeeSats = feePolicy.resolveOnchainNetworkFeeCapSats(request.amount());
        BigDecimal estimatedNetworkFee = feePolicy.estimateOnchainNetworkFee();
        feePolicy.validateOnchainNetworkFeeCap(request.amount(), estimatedNetworkFee);
        ExternalPaymentsCustodyPort.OnchainFundingPreflight preflight = custodyPort.preflightOnchain(
                new ExternalPaymentsCustodyPort.OnchainPreflightCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        request.toAddress(),
                        externalPaymentsMath.btcToSats(request.amount()),
                        maxFeeSats,
                        idempotencyRef));
        if (preflight != null && preflight.feeSats() > 0) {
            estimatedNetworkFee = externalPaymentsMath.satsToBtc(preflight.feeSats());
            feePolicy.validateOnchainNetworkFeeCap(request.amount(), estimatedNetworkFee);
        }
        BigDecimal platformFee = feePolicy.calculateWithdrawalFee(userId, request.amount());
        BigDecimal totalDebited = externalPaymentsMath.normalizeBtc(
                request.amount().add(estimatedNetworkFee).add(platformFee));

        ledgerPort.ensureBalance(wallet.getId(), totalDebited);

        String context = "EXTERNAL_ONCHAIN_PAYMENT:" + externalPaymentsMath.safeText(request.description());
        ledgerPort.updateBalance(wallet.getId(), totalDebited.negate(), context);

        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "ONCHAIN",
                "OUTBOUND_PAYMENT",
                "PROVIDER_PENDING",
                resolveProviderName(),
                request.toAddress(),
                null,
                null,
                null,
                null,
                null,
                request.amount(),
                estimatedNetworkFee,
                platformFee,
                totalDebited,
                null,
                request.description()));
        transfer.setConfirmations(0);
        transfer.setDetectedAt(LocalDateTime.now());
        transfer = externalTransfersPort.save(transfer);

        source.transactions.model.ExternalProviderOutboxEntity outbox = outboxService.enqueue(
                transfer.getId(),
                "ONCHAIN_SEND",
                idempotencyRef,
                "{\"destination\":\"" + request.toAddress() + "\",\"amountSats\":"
                        + externalPaymentsMath.btcToSats(request.amount()) + ",\"maxFeeSats\":"
                        + maxFeeSats + "}");

        ExternalPaymentsCustodyPort.PaymentResult payment;
        try {
            payment = custodyPort.sendOnchain(
                    new ExternalPaymentsCustodyPort.OnchainPaymentCommand(
                            userId,
                            wallet.getId(),
                            wallet.getName(),
                            request.toAddress(),
                            externalPaymentsMath.btcToSats(request.amount()),
                            maxFeeSats,
                            request.description(),
                            idempotencyRef,
                            authorization.platformSignature()));
        } catch (ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous ambiguousResult) {
            transfer.setStatus("AUTO_RESOLUTION_PENDING");
            transfer.setExternalReference(externalPaymentsMath.firstNonBlank(
                    ambiguousResult.providerReference(),
                    transfer.getExternalReference()));
            transfer.setProviderPayload(ambiguousResult.rawPayload());
            transfer.setDetectedAt(LocalDateTime.now());
            transfer = externalTransfersPort.save(transfer);
            outboxService.markUnknown(
                    outbox.getId(),
                    ambiguousResult.providerReference(),
                    ambiguousResult.getMessage());
            return externalTransferFactory.toResponseDTO(transfer);
        } catch (RuntimeException providerFailure) {
            ledgerPort.updateBalance(wallet.getId(), totalDebited, "ONCHAIN_PAYMENT_PROVIDER_FAILURE_COMPENSATION");
            transfer.setStatus("PROVIDER_FAILED");
            transfer.setProviderPayload("providerFailure=" + providerFailure.getClass().getSimpleName());
            externalTransfersPort.save(transfer);
            outboxService.markFailed(outbox.getId(), providerFailure.getMessage(), false);
            throw providerFailure;
        }

        BigDecimal networkFee = payment.feeSats() > 0
                ? externalPaymentsMath.satsToBtc(payment.feeSats())
                : estimatedNetworkFee;
        if (networkFee.compareTo(estimatedNetworkFee) < 0) {
            BigDecimal feeRefund = externalPaymentsMath.normalizeBtc(estimatedNetworkFee.subtract(networkFee));
            ledgerPort.updateBalance(wallet.getId(), feeRefund, "ONCHAIN_NETWORK_FEE_REFUND");
            totalDebited = totalDebited.subtract(feeRefund);
        } else if (networkFee.compareTo(estimatedNetworkFee) > 0) {
            BigDecimal additionalFee = externalPaymentsMath.normalizeBtc(networkFee.subtract(estimatedNetworkFee));
            ledgerPort.ensureBalance(wallet.getId(), additionalFee);
            ledgerPort.updateBalance(wallet.getId(), additionalFee.negate(), "ONCHAIN_NETWORK_FEE_ADJUSTMENT");
            totalDebited = totalDebited.add(additionalFee);
        }
        String externalReference = externalPaymentsMath.firstNonBlank(payment.txid(), payment.providerReference());
        transfer.setStatus(externalPaymentsMath.firstNonBlank(payment.status(), "MEMPOOL"));
        transfer.setExternalReference(externalReference);
        transfer.setBlockchainTxid(payment.txid());
        transfer.setNetworkFeeBtc(networkFee);
        transfer.setTotalDebitedBtc(totalDebited);
        transfer.setConfirmations(0);
        transfer.setDetectedAt(LocalDateTime.now());
        transfer.setProviderPayload(payment.rawPayload());
        transfer = externalTransfersPort.save(transfer);
        outboxService.markDispatched(outbox.getId(), externalReference);

        ledgerPort.recordPlatformFee(transfer.getId(), userId, totalDebited, platformFee);
        ledgerPort.recordHistory(new ExternalPaymentsLedgerPort.HistoryRecord(
                userId,
                wallet.getName(),
                request.toAddress(),
                "EXTERNAL_ONCHAIN_WITHDRAWAL",
                request.amount(),
                networkFee,
                transfer.getStatus(),
                payment.txid(),
                request.description(),
                LocalDateTime.now()));
        notificationPort.notifyUser(
                userId,
                NotificationMessages.payload(
                        NotificationKind.PAYMENT_SENT,
                        NotificationSeverity.SUCCESS,
                        NotificationMessageKey.EXTERNAL_ONCHAIN_PAYMENT_SENT,
                        "/history",
                        "external_transfer",
                        transfer.getId() != null ? transfer.getId().toString() : null,
                        Map.of(
                                "walletName", wallet.getName(),
                                "amountBtc", request.amount().toPlainString(),
                                "network", "ONCHAIN",
                                "destination", request.toAddress())));

        return externalTransferFactory.toResponseDTO(transfer);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for on-chain payments.");
        }
        if (idempotencyKey.length() > 96) {
            throw new IllegalArgumentException("idempotencyKey must have at most 96 characters.");
        }
    }

    private String resolveProviderName() {
        return custodyPort.providerName() != null ? custodyPort.providerName() : localAddressProviderName;
    }
}
