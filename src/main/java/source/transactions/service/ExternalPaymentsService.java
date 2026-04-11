package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.service.AddressDerivationService;
import source.ledger.entity.LedgerEntry;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.LightningInvoiceRequestDTO;
import source.transactions.dto.LightningInvoiceResponseDTO;
import source.transactions.dto.LightningPaymentRequestDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.dto.WalletNetworkAddressDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.MempoolClient;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalTransferRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ExternalPaymentsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExternalPaymentsService.class);

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AddressDerivationService addressDerivationService;
    private final CustodyGateway custodyGateway;
    private final ExternalTransferRepository externalTransferRepository;
    private final WalletAuthorizationService walletAuthorizationService;
    private final MempoolClient mempoolClient;
    private final source.notification.service.NotificationService notificationService;

    private final BigDecimal externalFeeRate;
    private final long defaultLightningMaxFeeSats;
    private final String localAddressProviderName;

    public ExternalPaymentsService(
            WalletService walletService,
            WalletRepository walletRepository,
            LedgerService ledgerService,
            LedgerTransactionHistoryRepository historyRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AddressDerivationService addressDerivationService,
            CustodyGateway custodyGateway,
            ExternalTransferRepository externalTransferRepository,
            WalletAuthorizationService walletAuthorizationService,
            MempoolClient mempoolClient,
            source.notification.service.NotificationService notificationService,
            @Value("${transactions.external.fee-rate:0.009}") BigDecimal externalFeeRate,
            @Value("${lightning.default-max-routing-fee-sats:60}") long defaultLightningMaxFeeSats,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.historyRepository = historyRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.addressDerivationService = addressDerivationService;
        this.custodyGateway = custodyGateway;
        this.externalTransferRepository = externalTransferRepository;
        this.walletAuthorizationService = walletAuthorizationService;
        this.mempoolClient = mempoolClient;
        this.notificationService = notificationService;
        this.externalFeeRate = externalFeeRate;
        this.defaultLightningMaxFeeSats = defaultLightningMaxFeeSats;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public WalletNetworkAddressDTO issueOnchainAddress(Long userId, OnchainAddressRequestDTO request) {
        WalletEntity wallet = resolveWallet(userId, request.walletName());
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        if (!regenerate && wallet.getDepositAddress() != null && !wallet.getDepositAddress().isBlank()) {
            return toWalletNetworkAddress(wallet, resolveProviderName());
        }

        String address;
        String externalReference;
        String provider = resolveProviderName();

        if (custodyGateway.isLive()) {
            CustodyGateway.GeneratedOnchainAddress issued = custodyGateway.createOnchainAddress(
                    new CustodyGateway.OnchainAddressCommand(userId, wallet.getId(), wallet.getName(), "wallet:" + wallet.getName()));
            address = issued.address();
            externalReference = firstNonBlank(issued.walletReference(), issued.providerReference());
        } else if (wallet.getXpub() != null && !wallet.getXpub().isBlank()) {
            int index = walletService.incrementLastDerivedIndex(wallet.getId());
            address = addressDerivationService.deriveAddressFromXpub(wallet.getXpub(), index);
            externalReference = "XPUB_INDEX_" + index;
            provider = localAddressProviderName;
        } else {
            address = addressDerivationService.deriveAddress(wallet.getId(), wallet.getPassphraseHash());
            externalReference = "STATIC_DERIVATION";
            provider = localAddressProviderName;
        }

        wallet.setDepositAddress(address);
        wallet.setExternalWalletReference(externalReference);
        walletRepository.save(wallet);

        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setUserId(userId);
        transfer.setWalletId(wallet.getId());
        transfer.setWalletNameSnapshot(wallet.getName());
        transfer.setNetwork("ONCHAIN");
        transfer.setTransferType("ADDRESS_ISSUE");
        transfer.setStatus("COMPLETED");
        transfer.setProvider(provider);
        transfer.setDestination(address);
        transfer.setExternalReference(externalReference);
        transfer.setContext("On-chain deposit address issued for wallet " + wallet.getName());
        externalTransferRepository.save(transfer);

        return new WalletNetworkAddressDTO(
                wallet.getName(),
                address,
                wallet.getLightningAddress(),
                provider,
                externalReference);
    }

    public WalletNetworkAddressDTO getWalletNetworkProfile(Long userId, String walletName) {
        WalletEntity wallet = resolveWallet(userId, walletName);
        return toWalletNetworkAddress(wallet, resolveProviderName());
    }

    @Transactional
    public LightningInvoiceResponseDTO createLightningInvoice(Long userId, LightningInvoiceRequestDTO request) {
        WalletEntity wallet = resolveWallet(userId, request.walletName());
        validatePositiveAmount(request.amount(), "Lightning invoice amount must be positive.");

        long amountSats = btcToSats(request.amount());
        int expiresInSeconds = request.expiresInSeconds() != null && request.expiresInSeconds() > 0
                ? request.expiresInSeconds()
                : 900;

        CustodyGateway.GeneratedLightningInvoice invoice = custodyGateway.createLightningInvoice(
                new CustodyGateway.LightningInvoiceCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        amountSats,
                        request.memo(),
                        expiresInSeconds));

        if (invoice.paymentRequest() == null || invoice.paymentRequest().isBlank()) {
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "The custody provider did not return a Lightning invoice.");
        }

        if (invoice.lightningAddress() != null && !invoice.lightningAddress().isBlank()) {
            wallet.setLightningAddress(invoice.lightningAddress());
            walletRepository.save(wallet);
        }

        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setUserId(userId);
        transfer.setWalletId(wallet.getId());
        transfer.setWalletNameSnapshot(wallet.getName());
        transfer.setNetwork("LIGHTNING");
        transfer.setTransferType("INBOUND_INVOICE");
        transfer.setStatus("PENDING");
        transfer.setProvider(resolveProviderName());
        transfer.setDestination(invoice.lightningAddress());
        transfer.setExternalReference(firstNonBlank(invoice.paymentHash(), invoice.providerReference()));
        transfer.setInvoiceData(invoice.paymentRequest());
        transfer.setAmountBtc(request.amount().setScale(8, RoundingMode.HALF_UP));
        transfer.setContext(request.memo());
        externalTransferRepository.save(transfer);

        return new LightningInvoiceResponseDTO(
                transfer.getId(),
                wallet.getName(),
                invoice.paymentRequest(),
                invoice.paymentHash(),
                invoice.lightningAddress(),
                request.amount().setScale(8, RoundingMode.HALF_UP),
                resolveProviderName(),
                invoice.expiresAt(),
                transfer.getStatus());
    }

    @Transactional
    public ExternalTransferResponseDTO sendOnchain(Long userId, OnchainSendRequestDTO request) {
        validatePositiveAmount(request.amount(), "On-chain payment amount must be positive.");
        if (!isValidBitcoinAddress(request.toAddress())) {
            throw new ExternalPaymentsExceptions.InvalidNetworkAddress(
                    "The destination Bitcoin address is invalid or unsupported.");
        }

        WalletEntity wallet = resolveWallet(userId, request.fromWalletName());
        WalletAuthorizationService.AuthorizationResult authorization = walletAuthorizationService.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        BigDecimal networkFee = estimateOnchainNetworkFee(request.amount());
        BigDecimal platformFee = calculatePlatformFee(request.amount());
        BigDecimal totalDebited = request.amount().add(networkFee).add(platformFee).setScale(8, RoundingMode.HALF_UP);

        ensureBalance(wallet.getId(), totalDebited);

        String context = "EXTERNAL_ONCHAIN_PAYMENT:" + safeText(request.description());
        ledgerService.updateBalance(wallet.getId(), totalDebited.negate(), context);

        CustodyGateway.PaymentResult payment = custodyGateway.sendOnchain(
                new CustodyGateway.OnchainPaymentCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        request.toAddress(),
                        btcToSats(request.amount()),
                        request.description(),
                        authorization.platformSignature()));

        String externalReference = firstNonBlank(payment.txid(), payment.providerReference());
        ExternalTransferEntity transfer = persistExternalTransfer(
                wallet,
                "ONCHAIN",
                "OUTBOUND_PAYMENT",
                firstNonBlank(payment.status(), "PENDING"),
                resolveProviderName(),
                request.toAddress(),
                externalReference,
                null,
                request.amount(),
                networkFee,
                platformFee,
                totalDebited,
                request.description());

        recordPlatformFee(transfer.getId(), userId, totalDebited, platformFee);
        recordHistory(
                userId,
                wallet.getName(),
                request.toAddress(),
                "EXTERNAL_ONCHAIN_WITHDRAWAL",
                request.amount(),
                networkFee,
                transfer.getStatus(),
                externalReference,
                request.description());
        notifyUser(userId, "Pagamento on-chain enviado",
                "Pagamento externo enviado para " + request.toAddress() + ".");

        return toResponseDTO(transfer);
    }

    @Transactional
    public ExternalTransferResponseDTO payLightning(Long userId, LightningPaymentRequestDTO request) {
        validatePositiveAmount(request.amount(), "Lightning payment amount must be positive.");
        if (request.paymentRequest() == null || request.paymentRequest().isBlank()) {
            throw new IllegalArgumentException("A valid Lightning invoice is required.");
        }

        WalletEntity wallet = resolveWallet(userId, request.fromWalletName());
        WalletAuthorizationService.AuthorizationResult authorization = walletAuthorizationService.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        BigDecimal reservedNetworkFee = normalizeBtc(
                request.maxRoutingFeeBtc() != null ? request.maxRoutingFeeBtc() : satsToBtc(defaultLightningMaxFeeSats));
        BigDecimal platformFee = calculatePlatformFee(request.amount());
        BigDecimal reservedTotal = request.amount().add(reservedNetworkFee).add(platformFee).setScale(8, RoundingMode.HALF_UP);

        ensureBalance(wallet.getId(), reservedTotal);
        ledgerService.updateBalance(wallet.getId(), reservedTotal.negate(), "EXTERNAL_LIGHTNING_PAYMENT:" + safeText(request.description()));

        CustodyGateway.PaymentResult payment = custodyGateway.payLightning(
                new CustodyGateway.LightningPaymentCommand(
                        userId,
                        wallet.getId(),
                        wallet.getName(),
                        request.paymentRequest(),
                        btcToSats(request.amount()),
                        btcToSats(reservedNetworkFee),
                        request.description(),
                        authorization.platformSignature()));

        BigDecimal actualFee = payment.feeSats() > 0 ? satsToBtc(payment.feeSats()) : reservedNetworkFee;
        if (actualFee.compareTo(reservedNetworkFee) < 0) {
            BigDecimal feeRefund = reservedNetworkFee.subtract(actualFee).setScale(8, RoundingMode.HALF_UP);
            ledgerService.updateBalance(wallet.getId(), feeRefund, "LIGHTNING_NETWORK_FEE_REFUND");
            reservedTotal = reservedTotal.subtract(feeRefund);
        } else if (actualFee.compareTo(reservedNetworkFee) > 0) {
            actualFee = reservedNetworkFee;
        }

        String externalReference = firstNonBlank(payment.paymentHash(), payment.providerReference());
        ExternalTransferEntity transfer = persistExternalTransfer(
                wallet,
                "LIGHTNING",
                "OUTBOUND_PAYMENT",
                firstNonBlank(payment.status(), "SETTLED"),
                resolveProviderName(),
                request.paymentRequest(),
                externalReference,
                request.paymentRequest(),
                request.amount(),
                actualFee,
                platformFee,
                reservedTotal,
                request.description());

        recordPlatformFee(transfer.getId(), userId, reservedTotal, platformFee);
        recordHistory(
                userId,
                wallet.getName(),
                externalReference != null ? externalReference : "LIGHTNING",
                "EXTERNAL_LIGHTNING_PAYMENT",
                request.amount(),
                actualFee,
                transfer.getStatus(),
                payment.paymentHash(),
                request.description());
        notifyUser(userId, "Pagamento Lightning enviado",
                "Pagamento Lightning externo encaminhado com sucesso.");

        return toResponseDTO(transfer);
    }

    public List<ExternalTransferResponseDTO> listTransfers(Long userId) {
        return externalTransferRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public ExternalTransferResponseDTO getTransfer(Long userId, UUID transferId) {
        ExternalTransferEntity transfer = externalTransferRepository.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new ExternalPaymentsExceptions.TransferNotFound(
                        "The requested external transfer could not be found."));
        return toResponseDTO(transfer);
    }

    private WalletEntity resolveWallet(Long userId, String walletName) {
        WalletEntity wallet = walletService.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new source.wallet.exceptions.WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }

    private WalletNetworkAddressDTO toWalletNetworkAddress(WalletEntity wallet, String provider) {
        return new WalletNetworkAddressDTO(
                wallet.getName(),
                wallet.getDepositAddress(),
                wallet.getLightningAddress(),
                provider,
                wallet.getExternalWalletReference());
    }

    private ExternalTransferEntity persistExternalTransfer(
            WalletEntity wallet,
            String network,
            String transferType,
            String status,
            String provider,
            String destination,
            String externalReference,
            String invoiceData,
            BigDecimal amountBtc,
            BigDecimal networkFeeBtc,
            BigDecimal platformFeeBtc,
            BigDecimal totalDebitedBtc,
            String context) {
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setUserId(wallet.getUser().getId());
        transfer.setWalletId(wallet.getId());
        transfer.setWalletNameSnapshot(wallet.getName());
        transfer.setNetwork(network);
        transfer.setTransferType(transferType);
        transfer.setStatus(status);
        transfer.setProvider(provider);
        transfer.setDestination(destination);
        transfer.setExternalReference(externalReference);
        transfer.setInvoiceData(invoiceData);
        transfer.setAmountBtc(normalizeBtc(amountBtc));
        transfer.setNetworkFeeBtc(normalizeBtc(networkFeeBtc));
        transfer.setPlatformFeeBtc(normalizeBtc(platformFeeBtc));
        transfer.setTotalDebitedBtc(normalizeBtc(totalDebitedBtc));
        transfer.setContext(context);
        return externalTransferRepository.save(transfer);
    }

    private void recordPlatformFee(UUID transferId, Long userId, BigDecimal totalDebited, BigDecimal platformFee) {
        LedgerEntry entry = new LedgerEntry(
                transferId,
                String.valueOf(userId),
                normalizeBtc(totalDebited).negate(),
                normalizeBtc(platformFee),
                "PENDING");
        ledgerEntryRepository.save(entry);
    }

    private void recordHistory(
            Long userId,
            String senderIdentifier,
            String receiverIdentifier,
            String transactionType,
            BigDecimal amount,
            BigDecimal networkFee,
            String status,
            String blockchainTxid,
            String context) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setSenderUserId(userId);
        history.setSenderIdentifier(senderIdentifier);
        history.setReceiverIdentifier(receiverIdentifier != null ? receiverIdentifier : "EXTERNAL");
        history.setTransactionType(transactionType);
        history.setAmount(normalizeBtc(amount));
        history.setNetworkFee(normalizeBtc(networkFee));
        history.setStatus(status != null ? status : "PENDING");
        history.setBlockchainTxid(blockchainTxid);
        history.setContext(context);
        history.setCreatedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    private void ensureBalance(Long walletId, BigDecimal requiredAmount) {
        BigDecimal current = ledgerService.getBalance(walletId);
        if (current.compareTo(requiredAmount) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Insufficient internal balance to cover the amount plus external fees.");
        }
    }

    private void validatePositiveAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private BigDecimal estimateOnchainNetworkFee(BigDecimal amount) {
        MempoolClient.RecommendedFees fees = mempoolClient.getRecommendedFees();
        long feeSats = Math.max(1L, fees.halfHourFee() * 225L);
        return satsToBtc(feeSats);
    }

    private BigDecimal calculatePlatformFee(BigDecimal amount) {
        return amount.multiply(externalFeeRate).setScale(8, RoundingMode.HALF_UP);
    }

    private ExternalTransferResponseDTO toResponseDTO(ExternalTransferEntity entity) {
        return new ExternalTransferResponseDTO(
                entity.getId(),
                entity.getNetwork(),
                entity.getTransferType(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getWalletNameSnapshot(),
                entity.getDestination(),
                entity.getAmountBtc(),
                entity.getNetworkFeeBtc(),
                entity.getPlatformFeeBtc(),
                entity.getTotalDebitedBtc(),
                entity.getExternalReference(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getContext());
    }

    private String resolveProviderName() {
        return custodyGateway.providerName() != null ? custodyGateway.providerName() : localAddressProviderName;
    }

    private boolean isValidBitcoinAddress(String address) {
        return address != null && address.matches("^(1|3|bc1)[a-zA-Z0-9]{25,62}$");
    }

    private long btcToSats(BigDecimal btc) {
        return btc.multiply(new BigDecimal("100000000"))
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeBtc(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void notifyUser(Long userId, String title, String body) {
        try {
            notificationService.notifyUser(userId, title, body);
        } catch (Exception ex) {
            log.warn("Failed to emit notification for external transfer: {}", ex.getMessage());
        }
    }
}
