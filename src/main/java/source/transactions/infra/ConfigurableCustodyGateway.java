package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import source.transactions.exception.ExternalPaymentsExceptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ConfigurableCustodyGateway implements CustodyGateway {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConfigurableCustodyGateway.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String providerName;
    private final String baseUrl;
    private final String apiKey;
    private final boolean mockMode;
    private final String onchainAddressPath;
    private final String lightningInvoicePath;
    private final String onchainSendPath;
    private final String lightningPayPath;

    public ConfigurableCustodyGateway(
            @Value("${custody.provider-name:BCX}") String providerName,
            @Value("${custody.base-url:}") String baseUrl,
            @Value("${custody.api-key:}") String apiKey,
            @Value("${custody.mock-mode:true}") boolean mockMode,
            @Value("${custody.onchain-address-path:/api/v1/onchain/address}") String onchainAddressPath,
            @Value("${custody.lightning-invoice-path:/api/v1/lightning/invoice}") String lightningInvoicePath,
            @Value("${custody.onchain-send-path:/api/v1/onchain/send}") String onchainSendPath,
            @Value("${custody.lightning-pay-path:/api/v1/lightning/pay}") String lightningPayPath) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.providerName = providerName;
        this.baseUrl = sanitizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.mockMode = mockMode;
        this.onchainAddressPath = onchainAddressPath;
        this.lightningInvoicePath = lightningInvoicePath;
        this.onchainSendPath = onchainSendPath;
        this.lightningPayPath = lightningPayPath;
    }

    @Override
    public boolean isLive() {
        return !mockMode && baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public GeneratedOnchainAddress createOnchainAddress(OnchainAddressCommand command) {
        ensureLiveForAddressIssuance();
        JsonNode response = post(onchainAddressPath, Map.of(
                "userId", command.userId(),
                "walletId", command.walletId(),
                "walletName", command.walletName(),
                "label", command.label()));

        String address = text(response, "address", "depositAddress");
        if (address == null || address.isBlank()) {
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "The custody provider did not return a valid on-chain address.");
        }

        return new GeneratedOnchainAddress(
                address,
                text(response, "walletReference", "walletId", "accountReference"),
                text(response, "reference", "id"));
    }

    @Override
    public GeneratedLightningInvoice createLightningInvoice(LightningInvoiceCommand command) {
        if (!isLive()) {
            String seed = command.walletName() + ":" + command.amountSats() + ":" + command.memo();
            String hash = sha256(seed);
            return new GeneratedLightningInvoice(
                    "lnbc" + Math.max(command.amountSats(), 1L) + "n1p" + hash.substring(0, 20),
                    hash,
                    command.walletName().toLowerCase() + "@kerosene.mock",
                    "mock-ln-" + hash.substring(0, 12),
                    LocalDateTime.now().plusSeconds(Math.max(command.expiresInSeconds(), 60)));
        }

        JsonNode response = post(lightningInvoicePath, Map.of(
                "userId", command.userId(),
                "walletId", command.walletId(),
                "walletName", command.walletName(),
                "amountSats", command.amountSats(),
                "memo", safeText(command.memo()),
                "expiresInSeconds", command.expiresInSeconds()));

        return new GeneratedLightningInvoice(
                text(response, "paymentRequest", "bolt11", "invoice"),
                text(response, "paymentHash", "hash"),
                text(response, "lightningAddress", "lnAddress"),
                text(response, "reference", "id"),
                parseDateTime(response, "expiresAt"));
    }

    @Override
    public PaymentResult sendOnchain(OnchainPaymentCommand command) {
        if (!isLive()) {
            String txid = sha256(command.destinationAddress() + ":" + command.amountSats() + ":" + command.description());
            return new PaymentResult(
                    "mock-onchain-" + txid.substring(0, 12),
                    txid,
                    null,
                    "PENDING",
                    0L,
                    "mock");
        }

        JsonNode response = post(onchainSendPath, Map.of(
                "userId", command.userId(),
                "walletId", command.walletId(),
                "walletName", command.walletName(),
                "destinationAddress", command.destinationAddress(),
                "amountSats", command.amountSats(),
                "description", safeText(command.description()),
                "authorizationProof", safeText(command.authorizationProof())));

        return new PaymentResult(
                text(response, "reference", "id"),
                text(response, "txid", "transactionId"),
                null,
                textOrDefault(response, "status", "PENDING"),
                longValue(response, "feeSats", "fee"),
                response.toString());
    }

    @Override
    public PaymentResult payLightning(LightningPaymentCommand command) {
        if (!isLive()) {
            String paymentHash = sha256(command.paymentRequest() + ":" + command.amountSats());
            long fee = Math.min(Math.max(command.maxFeeSats(), 1L), 30L);
            return new PaymentResult(
                    "mock-lightning-" + paymentHash.substring(0, 12),
                    null,
                    paymentHash,
                    "SETTLED",
                    fee,
                    "mock");
        }

        JsonNode response = post(lightningPayPath, Map.of(
                "userId", command.userId(),
                "walletId", command.walletId(),
                "walletName", command.walletName(),
                "paymentRequest", command.paymentRequest(),
                "amountSats", command.amountSats(),
                "maxFeeSats", command.maxFeeSats(),
                "description", safeText(command.description()),
                "authorizationProof", safeText(command.authorizationProof())));

        return new PaymentResult(
                text(response, "reference", "id"),
                text(response, "txid", "transactionId"),
                text(response, "paymentHash", "hash"),
                textOrDefault(response, "status", "SETTLED"),
                longValue(response, "feeSats", "fee"),
                response.toString());
    }

    private void ensureLiveForAddressIssuance() {
        if (!isLive()) {
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "Live custody integration is not configured for on-chain address issuance.");
        }
    }

    private JsonNode post(String path, Map<String, ?> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + path, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                        "The custody provider returned an invalid response for " + path + ".");
            }
            return objectMapper.readTree(response.getBody());
        } catch (ExternalPaymentsExceptions.CustodyProviderUnavailable ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[CustodyGateway] Provider call failed on {}: {}", path, ex.getMessage(), ex);
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "Unable to complete the custody provider request at the moment.");
        }
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private String textOrDefault(JsonNode node, String fieldName, String fallback) {
        String value = text(node, fieldName);
        return value != null ? value : fallback;
    }

    private long longValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0L;
    }

    private LocalDateTime parseDateTime(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isTextual()) {
            try {
                return LocalDateTime.parse(value.asText());
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now().plusMinutes(15);
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    private String sanitizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for custody mock responses", e);
        }
    }
}
