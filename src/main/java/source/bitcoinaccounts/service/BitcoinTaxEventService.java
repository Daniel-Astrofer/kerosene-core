package source.bitcoinaccounts.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.TaxEventEntity;
import source.bitcoinaccounts.repository.TaxEventRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BitcoinTaxEventService {

    private final TaxEventRepository repository;
    private final long readableRetentionHours;

    public BitcoinTaxEventService(
            TaxEventRepository repository,
            @Value("${bitcoin-accounts.readable-retention-hours:24}") long readableRetentionHours) {
        this.repository = repository;
        this.readableRetentionHours = Math.max(1L, readableRetentionHours);
    }

    public TaxEventEntity recordTemporaryEvent(
            Long userId,
            BitcoinAccountEnums.TaxEventType eventType,
            long quantitySats,
            String sourceTxid,
            UUID accountId,
            UUID cardId,
            UUID walletId,
            String classification) {
        if (sourceTxid != null && !sourceTxid.isBlank()) {
            return repository.findFirstByUserIdAndEventTypeAndSourceTxid(userId, eventType, sourceTxid)
                    .orElseGet(() -> createTemporaryEvent(
                            userId,
                            eventType,
                            quantitySats,
                            sourceTxid,
                            accountId,
                            cardId,
                            walletId,
                            classification));
        }
        return createTemporaryEvent(
                userId,
                eventType,
                quantitySats,
                sourceTxid,
                accountId,
                cardId,
                walletId,
                classification);
    }

    private TaxEventEntity createTemporaryEvent(
            Long userId,
            BitcoinAccountEnums.TaxEventType eventType,
            long quantitySats,
            String sourceTxid,
            UUID accountId,
            UUID cardId,
            UUID walletId,
            String classification) {
        TaxEventEntity event = new TaxEventEntity();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setQuantitySats(quantitySats);
        event.setSourceTxid(sourceTxid);
        event.setAccountId(accountId);
        event.setCardId(cardId);
        event.setWalletId(walletId);
        event.setClassification(classification != null ? classification : "USER_CLASSIFICATION_PENDING");
        event.setMetadataRedacted("{\"retention\":\"mobile-local-source-of-truth\",\"ttlHours\":\""
                + readableRetentionHours + "\"}");
        event.setPurgeAfter(LocalDateTime.now().plusHours(readableRetentionHours));
        return repository.save(event);
    }

    public void purgeReadableEventsOlderThan(LocalDateTime cutoff) {
        for (TaxEventEntity event : repository.findTop200ByPurgeAfterBefore(cutoff)) {
            event.setSourceTxid(null);
            event.setMetadataRedacted("{\"purged\":\"true\",\"reason\":\"24h_readable_transaction_ttl\"}");
            repository.save(event);
        }
    }

    public List<Map<String, Object>> listTemporaryEvents(Long userId) {
        return repository.findTop500ByUserIdAndPurgeAfterAfterOrderByCreatedAtDesc(userId, LocalDateTime.now()).stream()
                .map(this::toView)
                .toList();
    }

    public TaxEventEntity classify(Long userId, UUID eventId, String classification) {
        TaxEventEntity event = repository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tax event not found."));
        event.setClassification(normalizeClassification(classification));
        event.setMetadataRedacted("{\"retention\":\"mobile-local-source-of-truth\",\"classifiedBy\":\"user\"}");
        return repository.save(event);
    }

    public Map<String, Object> export(Long userId, String format) {
        List<TaxEventEntity> events = repository
                .findTop500ByUserIdAndPurgeAfterAfterOrderByCreatedAtDesc(userId, LocalDateTime.now());
        String normalized = format != null ? format.trim().toLowerCase(Locale.ROOT) : "json";
        return "csv".equals(normalized) ? csvExport(events) : jsonExport(events);
    }

    private Map<String, Object> jsonExport(List<TaxEventEntity> events) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "json");
        result.put("filename", "kerosene-tax-events.json");
        result.put("educationalNotice",
                "Organizamos seus eventos para facilitar sua conferência. Este relatório não substitui orientação profissional.");
        result.put("events", events.stream().map(this::toView).toList());
        return result;
    }

    private Map<String, Object> csvExport(List<TaxEventEntity> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("created_at,event_type,asset,quantity_sats,classification,source_ref,account_id,card_id,wallet_id\n");
        for (TaxEventEntity event : events) {
            csv.append(safe(event.getCreatedAt()))
                    .append(',').append(safe(event.getEventType()))
                    .append(',').append(safe(event.getAsset()))
                    .append(',').append(event.getQuantitySats())
                    .append(',').append(safe(event.getClassification()))
                    .append(',').append(safe(redactedRef(event.getSourceTxid())))
                    .append(',').append(safe(event.getAccountId()))
                    .append(',').append(safe(event.getCardId()))
                    .append(',').append(safe(event.getWalletId()))
                    .append('\n');
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "csv");
        result.put("filename", "kerosene-tax-events.csv");
        result.put("educationalNotice",
                "Organizamos seus eventos para facilitar sua conferência. Este relatório não substitui orientação profissional.");
        result.put("content", csv.toString());
        return result;
    }

    private Map<String, Object> toView(TaxEventEntity event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", event.getId());
        view.put("createdAt", event.getCreatedAt());
        view.put("eventType", event.getEventType());
        view.put("asset", event.getAsset());
        view.put("quantitySats", event.getQuantitySats());
        view.put("classification", event.getClassification());
        view.put("sourceRef", redactedRef(event.getSourceTxid()));
        view.put("accountId", event.getAccountId());
        view.put("cardId", event.getCardId());
        view.put("walletId", event.getWalletId());
        view.put("purgeAfter", event.getPurgeAfter());
        return view;
    }

    private String normalizeClassification(String classification) {
        String normalized = classification != null ? classification.trim().toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "SELF_TRANSFER", "THIRD_PARTY_DEPOSIT", "SPEND", "FEE", "UNKNOWN" -> normalized;
            default -> "USER_CLASSIFICATION_PENDING";
        };
    }

    private String redactedRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(":");
        String txid = parts[0];
        String ref = txid.length() <= 16 ? txid : txid.substring(0, 8) + "..." + txid.substring(txid.length() - 8);
        return parts.length > 1 ? ref + ":" + parts[1] : ref;
    }

    private String safe(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") || text.contains("\n") || text.contains("\"")
                ? "\"" + text + "\""
                : text;
    }
}
