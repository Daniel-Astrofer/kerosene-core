package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.BtcPayServerCustodyGateway;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@ConditionalOnProperty(prefix = "btcpay", name = "enabled", havingValue = "true")
public class BtcPayWebhookService {

    private final ObjectMapper objectMapper;
    private final ExternalTransfersPort externalTransfersPort;
    private final BtcPayServerCustodyGateway custodyGateway;
    private final NetworkTransferLifecycleService lifecycleService;
    private final NetworkTransferEventService networkTransferEventService;
    private final String storeId;
    private final String webhookSecret;

    public BtcPayWebhookService(
            ObjectMapper objectMapper,
            ExternalTransfersPort externalTransfersPort,
            BtcPayServerCustodyGateway custodyGateway,
            NetworkTransferLifecycleService lifecycleService,
            NetworkTransferEventService networkTransferEventService,
            @Value("${btcpay.store-id}") String storeId,
            @Value("${btcpay.webhook-secret:}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.externalTransfersPort = externalTransfersPort;
        this.custodyGateway = custodyGateway;
        this.lifecycleService = lifecycleService;
        this.networkTransferEventService = networkTransferEventService;
        this.storeId = storeId != null ? storeId.trim() : "";
        this.webhookSecret = webhookSecret != null ? webhookSecret.trim() : "";
    }

    public void handleWebhook(String incomingStoreId, String signatureHeader, String rawBody) {
        if (!storeId.equals(incomingStoreId)) {
            throw new IllegalArgumentException("BTCPay webhook store id does not match configured store.");
        }
        validateSignature(signatureHeader, rawBody);

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String eventType = text(payload, "type", "eventType");
            String invoiceId = resolveInvoiceId(payload);
            if (invoiceId == null || invoiceId.isBlank()) {
                networkTransferEventService.warn((Long) null, "BTCPAY_WEBHOOK_IGNORED", eventType, rawBody);
                return;
            }

            ExternalTransferEntity transfer = externalTransfersPort.findByInvoiceId(invoiceId).orElse(null);
            if (transfer == null) {
                networkTransferEventService.warn((Long) null, "BTCPAY_WEBHOOK_ORPHAN", invoiceId, rawBody);
                return;
            }

            CustodyGateway.IncomingLightningInvoiceStatus status = custodyGateway.getLightningInvoiceStatus(
                    new CustodyGateway.LightningInvoiceStatusCommand(
                            transfer.getUserId(),
                            transfer.getWalletId(),
                            transfer.getWalletNameSnapshot(),
                            transfer.getPaymentHash(),
                            transfer.getInvoiceId(),
                            transfer.getInvoiceData()));

            lifecycleService.reconcileLightningInvoice(
                    transfer,
                    normalizeStatus(eventType, status.status()),
                    status.receivedSats() != null ? status.receivedSats() : 0L,
                    transfer.getPaymentHash(),
                    status.rawPayload(),
                    "BTCPAY_WEBHOOK");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process BTCPay webhook.", ex);
        }
    }

    private void validateSignature(String signatureHeader, String rawBody) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("BTCPay webhook secret is not configured.");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Missing BTCPAY-SIG header.");
        }

        String provided = signatureHeader.contains("=")
                ? signatureHeader.substring(signatureHeader.lastIndexOf('=') + 1)
                : signatureHeader;
        String expected = hmacSha256(rawBody, webhookSecret);
        if (!MessageDigest.isEqual(
                provided.trim().toLowerCase().getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid BTCPay webhook signature.");
        }
    }

    private String normalizeStatus(String eventType, String invoiceStatus) {
        String event = eventType != null ? eventType.trim().toUpperCase() : "";
        if (event.contains("EXPIRED") || event.contains("INVALID")) {
            return event.contains("EXPIRED") ? "EXPIRED" : "FAILED";
        }
        if (event.contains("SETTLED") || event.contains("PROCESSING")) {
            return invoiceStatus;
        }
        return invoiceStatus != null ? invoiceStatus : "PENDING";
    }

    private String resolveInvoiceId(JsonNode payload) {
        String direct = text(payload, "invoiceId");
        if (direct != null) {
            return direct;
        }
        JsonNode invoice = payload.path("invoice");
        direct = text(invoice, "id", "invoiceId");
        if (direct != null) {
            return direct;
        }
        JsonNode data = payload.path("data");
        direct = text(data, "id", "invoiceId");
        if (direct != null) {
            return direct;
        }
        JsonNode resource = payload.path("resource");
        return text(resource, "id", "invoiceId");
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to validate BTCPay webhook signature.", ex);
        }
    }
}
