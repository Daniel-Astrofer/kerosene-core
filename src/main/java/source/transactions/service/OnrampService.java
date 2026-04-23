package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalTransferFactory;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class OnrampService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OnrampService.class);

    private final WalletService walletService;
    private final StringRedisTemplate redisTemplate;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final CustodialAddressAllocator custodialAddressAllocator;

    @Value("${onramp.moonpay.url:https://buy.moonpay.com}")
    private String moonpayBaseUrl;

    @Value("${onramp.moonpay.api-key:}")
    private String moonpayApiKey;

    @Value("${onramp.moonpay.secret-key:}")
    private String moonpaySecretKey;

    @Value("${onramp.moonpay.base-currency-code:btc}")
    private String moonpayBaseCurrencyCode;

    @Value("${onramp.banxa.url:https://checkout.banxa.com}")
    private String banxaBaseUrl;

    @Value("${onramp.banxa.fiat-type:USD}")
    private String banxaFiatType;

    @Value("${onramp.bipa.url:https://bipa.app/buy/btc}")
    private String bipaBaseUrl;

    public OnrampService(
            WalletService walletService,
            StringRedisTemplate redisTemplate,
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            ExternalPaymentsMath externalPaymentsMath,
            CustodialAddressAllocator custodialAddressAllocator) {
        this.walletService = walletService;
        this.redisTemplate = redisTemplate;
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.externalPaymentsMath = externalPaymentsMath;
        this.custodialAddressAllocator = custodialAddressAllocator;
    }

    public Map<String, String> generateOnrampUrls(Long userId) {
        return generateOnrampUrls(userId, null, null);
    }

    @Transactional
    public Map<String, String> generateOnrampUrls(Long userId, String walletName, BigDecimal amountBtc) {
        enforceRateLimit(userId);

        WalletEntity wallet = resolveWallet(userId, walletName);
        AddressAllocation allocation = allocateTrackedOnchainAddress(userId, wallet);
        BigDecimal normalizedAmount = normalizeOptionalAmount(amountBtc);

        var transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "ONCHAIN",
                "ONRAMP_PURCHASE",
                "PENDING",
                "ONRAMP",
                allocation.address(),
                allocation.externalReference(),
                null,
                normalizedAmount,
                null,
                null,
                normalizedAmount,
                null,
                "Onramp checkout created for wallet " + wallet.getName()));

        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("moonpay", buildMoonpayUrl(allocation.address(), normalizedAmount, transfer.getId()));
        urls.put("banxa", buildBanxaUrl(allocation.address(), normalizedAmount, transfer.getId()));
        urls.put("bipa", buildBipaUrl(allocation.address(), normalizedAmount, transfer.getId()));
        urls.put("transferId", transfer.getId().toString());
        urls.put("depositAddress", allocation.address());
        urls.put("walletName", wallet.getName());

        return urls;
    }

    private void enforceRateLimit(Long userId) {
        String rateLimitKey = "rl:onramp:" + userId;
        String currentCount = redisTemplate.opsForValue().get(rateLimitKey);
        if (currentCount != null && Integer.parseInt(currentCount) >= 5) {
            log.warn("[RateLimit] Too many onramp sessions for user {}. Blocking.", userId);
            throw new RuntimeException("ADDRESS_GENERATION_LIMIT_EXCEEDED: Max 5 addresses per hour.");
        }

        redisTemplate.opsForValue().increment(rateLimitKey);
        redisTemplate.expire(rateLimitKey, Duration.ofHours(1));
    }

    private WalletEntity resolveWallet(Long userId, String walletName) {
        if (walletName != null && !walletName.isBlank()) {
            WalletEntity wallet = walletService.findByNameAndUserId(walletName, userId);
            if (wallet == null) {
                throw new IllegalStateException("Requested wallet was not found for onramp checkout.");
            }
            return wallet;
        }

        List<WalletEntity> wallets = walletService.findByUserId(userId);
        if (wallets == null || wallets.isEmpty()) {
            throw new IllegalStateException("User has no wallet address to receive funds.");
        }
        return wallets.get(0);
    }

    private AddressAllocation allocateTrackedOnchainAddress(Long userId, WalletEntity wallet) {
        CustodialAddressAllocator.Allocation allocation = custodialAddressAllocator.allocate(
                userId,
                wallet,
                "onramp:" + wallet.getName(),
                true);
        registerAddressWatch(allocation.address(), wallet, allocation.externalReference());
        log.info("[Onramp] Issued dedicated custodial address {} for wallet {} (ref={})",
                allocation.address(), wallet.getId(), allocation.externalReference());
        return new AddressAllocation(allocation.address(), allocation.externalReference(), allocation.provider());
    }

    private void registerAddressWatch(String address, WalletEntity wallet, String externalReference) {
        String watchKey = "address_watch:" + address;
        String watchData = String.format(
                Locale.ROOT,
                "%d:%d:%s",
                wallet.getId(),
                wallet.getUser().getId(),
                externalReference != null ? externalReference : "NO_REF");
        redisTemplate.opsForValue().set(watchKey, watchData, Duration.ofDays(1));
    }

    private BigDecimal normalizeOptionalAmount(BigDecimal amountBtc) {
        return amountBtc != null && amountBtc.signum() > 0
                ? externalPaymentsMath.normalizeBtc(amountBtc)
                : null;
    }

    private String buildMoonpayUrl(String address, BigDecimal amountBtc, UUID transferId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(moonpayBaseUrl)
                .queryParam("currencyCode", "btc")
                .queryParam("walletAddress", address)
                .queryParam("externalCustomerId", transferId.toString());

        if (moonpayApiKey != null && !moonpayApiKey.isBlank()) {
            builder.queryParam("apiKey", moonpayApiKey);
        }

        if (amountBtc != null) {
            builder.queryParam("baseCurrencyCode", moonpayBaseCurrencyCode)
                    .queryParam("baseCurrencyAmount", amountBtc.toPlainString());
        }

        String unsignedUrl = builder.build().encode().toUriString();
        return appendMoonpaySignature(unsignedUrl);
    }

    private String buildBanxaUrl(String address, BigDecimal amountBtc, UUID transferId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(banxaBaseUrl)
                .queryParam("coinType", "BTC")
                .queryParam("blockchain", "BTC")
                .queryParam("fiatType", banxaFiatType)
                .queryParam("walletAddress", address)
                .queryParam("orderRef", transferId.toString());

        if (amountBtc != null) {
            builder.queryParam("coinAmount", amountBtc.toPlainString());
        }

        return builder.build(true).toUriString();
    }

    private String buildBipaUrl(String address, BigDecimal amountBtc, UUID transferId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(bipaBaseUrl)
                .queryParam("address", address)
                .queryParam("reference", transferId.toString());

        if (amountBtc != null) {
            builder.queryParam("amount", amountBtc.toPlainString());
        }

        return builder.build(true).toUriString();
    }

    private String appendMoonpaySignature(String unsignedUrl) {
        if (moonpaySecretKey == null || moonpaySecretKey.isBlank()) {
            return unsignedUrl;
        }

        try {
            URI uri = URI.create(unsignedUrl);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return unsignedUrl;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(moonpaySecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = "?" + query;
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return unsignedUrl + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("[Onramp] Failed to sign MoonPay URL. Falling back to unsigned URL: {}", ex.getMessage());
            return unsignedUrl;
        }
    }

    private record AddressAllocation(
            String address,
            String externalReference,
            String provider) {
    }
}
