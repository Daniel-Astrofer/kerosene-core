package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.WalletAuthorizationService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class SendOnchainPaymentUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final WalletAuthorizationService walletAuthorizationService;
    private final CustodyGateway custodyGateway;
    private final ExternalPaymentsFeePolicy feePolicy;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final String localAddressProviderName;

    public SendOnchainPaymentUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsNotificationPort notificationPort,
            WalletAuthorizationService walletAuthorizationService,
            CustodyGateway custodyGateway,
            ExternalPaymentsFeePolicy feePolicy,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.ledgerPort = ledgerPort;
        this.externalTransfersPort = externalTransfersPort;
        this.notificationPort = notificationPort;
        this.walletAuthorizationService = walletAuthorizationService;
        this.custodyGateway = custodyGateway;
        this.feePolicy = feePolicy;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public ExternalTransferResponseDTO send(Long userId, OnchainSendRequestDTO request) {
        externalPaymentsMath.validatePositiveAmount(request.amount(), "On-chain payment amount must be positive.");
        if (!externalPaymentsMath.isValidBitcoinAddress(request.toAddress())) {
            throw new ExternalPaymentsExceptions.InvalidNetworkAddress(
                    "The destination Bitcoin address is invalid or unsupported.");
        }

        WalletEntity wallet = walletPort.requireWallet(userId, request.fromWalletName());
        WalletAuthorizationService.AuthorizationResult authorization = walletAuthorizationService.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        BigDecimal networkFee = feePolicy.estimateOnchainNetworkFee();
        BigDecimal platformFee = feePolicy.calculateWithdrawalFee(userId, request.amount());
        BigDecimal totalDebited = externalPaymentsMath.normalizeBtc(
                request.amount().add(networkFee).add(platformFee));

        ledgerPort.ensureBalance(wallet.getId(), totalDebited);

        String context = "EXTERNAL_ONCHAIN_PAYMENT:" + externalPaymentsMath.safeText(request.description());
        ledgerPort.updateBalance(wallet.getId(), totalDebited.negate(), context);

        CustodyGateway.PaymentResult payment = custodyGateway.sendOnchain(
                new CustodyGateway.OnchainPaymentCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        request.toAddress(),
                        externalPaymentsMath.btcToSats(request.amount()),
                        request.description(),
                        authorization.platformSignature()));

        String externalReference = externalPaymentsMath.firstNonBlank(payment.txid(), payment.providerReference());
        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "ONCHAIN",
                "OUTBOUND_PAYMENT",
                externalPaymentsMath.firstNonBlank(payment.status(), "PENDING"),
                resolveProviderName(),
                request.toAddress(),
                externalReference,
                null,
                request.amount(),
                networkFee,
                platformFee,
                totalDebited,
                null,
                request.description()));

        ledgerPort.recordPlatformFee(transfer.getId(), userId, totalDebited, platformFee);
        ledgerPort.recordHistory(new ExternalPaymentsLedgerPort.HistoryRecord(
                userId,
                wallet.getName(),
                request.toAddress(),
                "EXTERNAL_ONCHAIN_WITHDRAWAL",
                request.amount(),
                networkFee,
                transfer.getStatus(),
                externalReference,
                request.description(),
                LocalDateTime.now()));
        notificationPort.notifyUser(
                userId,
                "Pagamento on-chain enviado",
                "Pagamento externo enviado para " + request.toAddress() + ".");

        return externalTransferFactory.toResponseDTO(transfer);
    }

    private String resolveProviderName() {
        return custodyGateway.providerName() != null ? custodyGateway.providerName() : localAddressProviderName;
    }
}
