package source.payments.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.model.entity.UserDataBase;
import source.common.service.TickerService;
import source.payments.dto.PaymentQuoteRequest;
import source.payments.dto.PaymentQuoteResponse;
import source.payments.exception.PaymentException;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentIntentRepository;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.infra.MempoolClient;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentQuoteService {

    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");
    private static final long ONCHAIN_DUST_LIMIT_SATS = 546L;

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReceivingCapabilityService receivingCapabilityService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentResponseMapper responseMapper;
    private final PaymentStateMachine paymentStateMachine;
    private final TickerService tickerService;
    private final MempoolClient mempoolClient;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final WalletCardProfileService walletCardProfileService;
    private final long quoteTtlSeconds;
    private final long lightningDefaultFeeSats;
    private final long onchainVbytesEstimate;
    private final BigDecimal fallbackPlatformFeeRate;

    public PaymentQuoteService(
            PaymentIntentRepository paymentIntentRepository,
            ReceivingCapabilityService receivingCapabilityService,
            PaymentAuditService paymentAuditService,
            PaymentResponseMapper responseMapper,
            PaymentStateMachine paymentStateMachine,
            TickerService tickerService,
            MempoolClient mempoolClient,
            ExternalPaymentsMath externalPaymentsMath,
            WalletCardProfileService walletCardProfileService,
            @Value("${payments.quote.ttl-seconds:120}") long quoteTtlSeconds,
            @Value("${lightning.default-max-routing-fee-sats:60}") long lightningDefaultFeeSats,
            @Value("${payments.onchain.vbytes-estimate:225}") long onchainVbytesEstimate,
            @Value("${payments.platform-fee-rate:0.009}") BigDecimal fallbackPlatformFeeRate) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.receivingCapabilityService = receivingCapabilityService;
        this.paymentAuditService = paymentAuditService;
        this.responseMapper = responseMapper;
        this.paymentStateMachine = paymentStateMachine;
        this.tickerService = tickerService;
        this.mempoolClient = mempoolClient;
        this.externalPaymentsMath = externalPaymentsMath;
        this.walletCardProfileService = walletCardProfileService;
        this.quoteTtlSeconds = quoteTtlSeconds;
        this.lightningDefaultFeeSats = lightningDefaultFeeSats;
        this.onchainVbytesEstimate = onchainVbytesEstimate;
        this.fallbackPlatformFeeRate = fallbackPlatformFeeRate;
    }

    @Transactional
    public PaymentQuoteResponse quote(Long senderUserId, PaymentQuoteRequest request) {
        validateSupportedAsset(request.asset());
        String fiatCurrency = normalizeCurrency(request.fiatCurrency());
        BigDecimal requestedFiat = parseFiat(request.amountFiat());
        BigDecimal fxRate = tickerService.getPrice(fiatCurrency);
        if (fxRate == null || fxRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentException.unavailable(
                    "PAYMENT_FX_RATE_UNAVAILABLE",
                    "Não foi possível obter a cotação agora. Tente novamente em instantes.");
        }

        Destination destination = resolveDestination(senderUserId, request);
        long requestedSats = convertFiatToSatoshis(requestedFiat, fxRate);
        if (requestedSats <= 0) {
            throw PaymentException.badRequest(
                    "PAYMENT_AMOUNT_TOO_LOW",
                    "O valor informado é baixo demais para envio.");
        }

        long networkFeeSats = calculateEstimatedNetworkFee(request.rail(), request.speed());
        BigDecimal platformRate = platformFeeRate(senderUserId);
        Amounts amounts = computePaymentAmounts(request.feeMode(), request.rail(), requestedSats, networkFeeSats, platformRate);
        validateAmounts(request.rail(), amounts);

        List<String> warnings = warningsFor(request.rail());
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setSenderUserId(senderUserId);
        intent.setReceiverUserId(destination.receiverUserId());
        intent.setReceiverDisplayName(destination.receiverDisplayName());
        intent.setReceiverIdentifier(clean(request.receiverIdentifier()));
        intent.setExternalDestination(clean(destination.externalDestination()));
        intent.setRail(request.rail());
        intent.setFeeMode(request.feeMode());
        intent.setRequestedAmountFiat(requestedFiat);
        intent.setFiatCurrency(fiatCurrency);
        intent.setAsset("BTC");
        intent.setRequestedAmountSats(requestedSats);
        intent.setReceiverAmountSats(amounts.receiverAmountSats());
        intent.setTotalDebitSats(amounts.totalDebitSats());
        intent.setNetworkFeeSats(amounts.networkFeeSats());
        intent.setKeroseneFeeSats(amounts.platformFeeSats());
        intent.setFxRate(fxRate.setScale(2, RoundingMode.HALF_UP));
        intent.setQuoteExpiresAt(Instant.now().plusSeconds(quoteTtlSeconds));
        paymentStateMachine.quote(intent);
        intent.setSpeed(request.speed());
        intent.setWarnings(String.join("|", warnings));

        PaymentIntentEntity saved = paymentIntentRepository.save(intent);
        paymentAuditService.record(senderUserId, saved.getId(), "PAYMENT_QUOTED", java.util.Map.of(
                "rail", saved.getRail().name(),
                "feeMode", saved.getFeeMode().name(),
                "receiverAmountSats", saved.getReceiverAmountSats(),
                "totalDebitSats", saved.getTotalDebitSats()));
        return responseMapper.toQuoteResponse(saved);
    }

    private Destination resolveDestination(Long senderUserId, PaymentQuoteRequest request) {
        return switch (request.rail()) {
            case INTERNAL -> resolveInternal(senderUserId, request.receiverIdentifier());
            case LIGHTNING -> resolveLightning(request.receiverIdentifier(), request.externalDestination());
            case ONCHAIN -> resolveOnchain(request.receiverIdentifier(), request.externalDestination());
        };
    }

    private Destination resolveInternal(Long senderUserId, String receiverIdentifier) {
        UserDataBase receiver = receivingCapabilityService.resolveUser(receiverIdentifier)
                .orElseThrow(() -> PaymentException.notFound(
                        "RECEIVER_NOT_FOUND",
                        "Não encontramos este usuário Kerosene."));
        if (!receivingCapabilityService.isActive(receiver)) {
            throw PaymentException.badRequest(
                    "RECEIVER_NOT_READY",
                    "Este usuário ainda não está pronto para receber fundos.");
        }
        if (receiver.getId().equals(senderUserId)) {
            throw PaymentException.badRequest(
                    "PAYMENT_SELF_TRANSFER_NOT_ALLOWED",
                    "Escolha outro usuário para concluir este envio.");
        }
        return new Destination(receiver.getId(), displayName(receiver), null);
    }

    private Destination resolveLightning(String receiverIdentifier, String externalDestination) {
        if (hasText(externalDestination)) {
            String normalized = clean(externalDestination);
            if (!isLightningDestination(normalized)) {
                throw PaymentException.badRequest(
                        "LIGHTNING_INVALID_DESTINATION",
                        "Este destino Lightning não parece válido.");
            }
            return new Destination(null, "Destino Lightning", normalized);
        }
        UserDataBase receiver = validateAndGetReceiver(receiverIdentifier);
        if (!receivingCapabilityService.canReceiveLightning(receiver.getId())) {
            throw PaymentException.badRequest(
                    "LIGHTNING_RECEIVER_METHOD_NOT_FOUND",
                    "Este usuário ainda não configurou recebimento Lightning.");
        }
        return new Destination(receiver.getId(), displayName(receiver), null);
    }

    private Destination resolveOnchain(String receiverIdentifier, String externalDestination) {
        if (hasText(externalDestination)) {
            String normalized = formatOnchainDestination(externalDestination);
            if (!externalPaymentsMath.isValidBitcoinAddress(normalized)) {
                throw PaymentException.badRequest(
                        "ONCHAIN_INVALID_ADDRESS",
                        "O endereço Bitcoin informado não é válido para esta rede.");
            }
            return new Destination(null, "Carteira Bitcoin", normalized);
        }
        UserDataBase receiver = validateAndGetReceiver(receiverIdentifier);
        if (!receivingCapabilityService.canReceiveOnchain(receiver.getId())) {
            throw PaymentException.badRequest(
                    "ONCHAIN_RECEIVER_METHOD_NOT_FOUND",
                    "Este usuário não possui carteira on-chain cadastrada para receber.");
        }
        return new Destination(receiver.getId(), displayName(receiver), null);
    }

    private UserDataBase validateAndGetReceiver(String receiverIdentifier) {
        UserDataBase receiver = receivingCapabilityService.resolveUser(receiverIdentifier)
                .orElseThrow(() -> PaymentException.notFound(
                        "RECEIVER_NOT_FOUND",
                        "Não encontramos este usuário Kerosene."));
        if (!receivingCapabilityService.isActive(receiver)) {
            throw PaymentException.badRequest(
                    "RECEIVER_NOT_READY",
                    "Este usuário ainda não está pronto para receber fundos.");
        }
        return receiver;
    }

    private Amounts computePaymentAmounts(
            PaymentEnums.FeeMode feeMode,
            PaymentEnums.PaymentRail rail,
            long requestedSats,
            long networkFeeSats,
            BigDecimal platformRate) {
        if (rail == PaymentEnums.PaymentRail.INTERNAL) {
            return new Amounts(requestedSats, requestedSats, 0L, 0L);
        }

        if (feeMode == PaymentEnums.FeeMode.SENDER_PAYS) {
            long platformFee = percentageFee(requestedSats, platformRate);
            return new Amounts(
                    requestedSats,
                    safeAdd(requestedSats, safeAdd(networkFeeSats, platformFee)),
                    networkFeeSats,
                    platformFee);
        }

        long platformFee = percentageFee(requestedSats, platformRate);
        long receiverAmount = requestedSats - networkFeeSats - platformFee;
        if (receiverAmount <= 0) {
            throw PaymentException.badRequest(
                    "PAYMENT_NET_AMOUNT_NEGATIVE",
                    "O valor líquido ficaria menor que zero após as taxas.");
        }
        return new Amounts(receiverAmount, requestedSats, networkFeeSats, platformFee);
    }

    private void validateAmounts(PaymentEnums.PaymentRail rail, Amounts amounts) {
        if (amounts.receiverAmountSats() <= 0 || amounts.totalDebitSats() <= 0) {
            throw PaymentException.badRequest(
                    "PAYMENT_AMOUNT_TOO_LOW",
                    "O valor informado é baixo demais para envio.");
        }
        if (rail == PaymentEnums.PaymentRail.ONCHAIN && amounts.receiverAmountSats() < ONCHAIN_DUST_LIMIT_SATS) {
            throw PaymentException.badRequest(
                    "ONCHAIN_AMOUNT_BELOW_DUST",
                    "O valor é baixo demais para envio on-chain depois das taxas.");
        }
    }

    private long calculateEstimatedNetworkFee(PaymentEnums.PaymentRail rail, PaymentEnums.OnchainSpeed speed) {
        return switch (rail) {
            case INTERNAL -> 0L;
            case LIGHTNING -> Math.max(0L, lightningDefaultFeeSats);
            case ONCHAIN -> {
                MempoolClient.RecommendedFees fees = mempoolClient.getRecommendedFees();
                long satsPerVbyte = switch (speed != null ? speed : PaymentEnums.OnchainSpeed.NORMAL) {
                    case ECONOMY -> fees.economyFee();
                    case FAST -> fees.fastestFee();
                    case NORMAL -> fees.halfHourFee();
                };
                yield Math.max(1L, satsPerVbyte * onchainVbytesEstimate);
            }
        };
    }

    private BigDecimal platformFeeRate(Long senderUserId) {
        try {
            return walletCardProfileService.resolveProfile(senderUserId).withdrawalFeeRate();
        } catch (RuntimeException exception) {
            return fallbackPlatformFeeRate;
        }
    }

    private long percentageFee(long baseSats, BigDecimal rate) {
        if (baseSats <= 0 || rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(baseSats).multiply(rate).setScale(0, RoundingMode.UP).longValueExact();
    }

    private long convertFiatToSatoshis(BigDecimal requestedFiat, BigDecimal fxRate) {
        return requestedFiat
                .divide(fxRate, 8, RoundingMode.DOWN)
                .multiply(SATS_PER_BTC)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw PaymentException.badRequest(
                    "PAYMENT_AMOUNT_TOO_HIGH",
                    "O valor informado excede o limite permitido.");
        }
    }

    private String formatOnchainDestination(String destination) {
        String normalized = clean(destination);
        if (normalized.toLowerCase(Locale.ROOT).startsWith("bitcoin:")) {
            String withoutScheme = normalized.substring("bitcoin:".length());
            int queryIndex = withoutScheme.indexOf('?');
            return queryIndex >= 0 ? withoutScheme.substring(0, queryIndex) : withoutScheme;
        }
        return normalized;
    }

    private boolean isLightningDestination(String destination) {
        String normalized = destination.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("lnbc")
                || normalized.startsWith("lntb")
                || normalized.startsWith("lnbcrt")
                || normalized.startsWith("lnurl")
                || normalized.contains("@");
    }

    private List<String> warningsFor(PaymentEnums.PaymentRail rail) {
        List<String> warnings = new ArrayList<>();
        if (rail == PaymentEnums.PaymentRail.ONCHAIN || rail == PaymentEnums.PaymentRail.LIGHTNING) {
            warnings.add("Pagamentos Bitcoin são irreversíveis após enviados.");
        }
        return warnings;
    }

    private String displayName(UserDataBase user) {
        return "@" + user.getUsername();
    }

    private void validateSupportedAsset(String asset) {
        if (!"BTC".equalsIgnoreCase(clean(asset))) {
            throw PaymentException.badRequest(
                    "PAYMENT_ASSET_NOT_SUPPORTED",
                    "No momento, este envio está disponível apenas para Bitcoin.");
        }
    }

    private String normalizeCurrency(String currency) {
        String normalized = clean(currency).toUpperCase(Locale.ROOT);
        if (!"BRL".equals(normalized)) {
            throw PaymentException.badRequest(
                    "PAYMENT_FIAT_NOT_SUPPORTED",
                    "No momento, a cotação deste envio está disponível em reais.");
        }
        return normalized;
    }

    private BigDecimal parseFiat(String amountFiat) {
        try {
            BigDecimal amount = new BigDecimal(clean(amountFiat).replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return amount;
        } catch (RuntimeException exception) {
            throw PaymentException.badRequest(
                    "PAYMENT_INVALID_AMOUNT",
                    "Informe um valor válido para continuar.");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Destination(Long receiverUserId, String receiverDisplayName, String externalDestination) {
    }

    private record Amounts(long receiverAmountSats, long totalDebitSats, long networkFeeSats, long platformFeeSats) {
    }
}
