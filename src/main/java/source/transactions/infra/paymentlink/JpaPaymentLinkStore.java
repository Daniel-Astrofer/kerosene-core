package source.transactions.infra.paymentlink;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.model.PaymentLinkEntity;
import source.transactions.repository.PaymentLinkRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Primary
@Component
public class JpaPaymentLinkStore implements PaymentLinkStore {

    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {
    };

    private final PaymentLinkRepository paymentLinkRepository;
    private final ObjectMapper objectMapper;

    public JpaPaymentLinkStore(PaymentLinkRepository paymentLinkRepository, ObjectMapper objectMapper) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentLinkDTO save(PaymentLinkDTO paymentLink) {
        return save(paymentLink, Duration.ZERO);
    }

    @Override
    @Transactional
    public PaymentLinkDTO save(PaymentLinkDTO paymentLink, Duration ttl) {
        PaymentLinkEntity entity = toEntity(paymentLink);
        paymentLinkRepository.save(entity);
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentLinkDTO> findById(String linkId) {
        return paymentLinkRepository.findById(linkId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentLinkDTO> findByUserId(Long userId) {
        return paymentLinkRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentLinkDTO> findByStatus(String status) {
        return paymentLinkRepository.findTop200ByStatusAndExpiresAtAfterOrderByCreatedAtAsc(
                        status,
                        LocalDateTime.now())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String linkId) {
        paymentLinkRepository.deleteById(linkId);
    }

    private PaymentLinkEntity toEntity(PaymentLinkDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        PaymentLinkEntity entity = paymentLinkRepository.findById(dto.getId()).orElseGet(PaymentLinkEntity::new);
        entity.setId(dto.getId());
        entity.setUserId(dto.getUserId());
        entity.setSessionId(blankToNull(dto.getSessionId()));
        entity.setAmountBtc(dto.getAmountBtc());
        entity.setGrossAmountBtc(dto.getGrossAmountBtc());
        entity.setDepositFeeBtc(dto.getDepositFeeBtc());
        entity.setNetAmountBtc(dto.getNetAmountBtc());
        entity.setDescription(trim(dto.getDescription(), 255));
        entity.setDepositAddress(dto.getDepositAddress());
        entity.setVisibility(dto.getVisibility());
        entity.setConfirmationMode(dto.getConfirmationMode());
        entity.setAmountLocked(dto.getAmountLocked() == null || dto.getAmountLocked());
        entity.setReferenceLabel(trim(dto.getReferenceLabel(), 64));
        entity.setMetadataJson(writeMetadata(dto.getMetadata()));
        entity.setStatus(dto.getStatus());
        entity.setTxid(blankToNull(dto.getTxid()));
        entity.setExpiresAt(dto.getExpiresAt());
        entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        entity.setPaidAt(dto.getPaidAt());
        entity.setCompletedAt(dto.getCompletedAt());
        entity.setCancelledAt(dto.getCancelledAt());
        entity.setCancelReason(trim(dto.getCancelReason(), 255));
        entity.setUpdatedAt(now);
        return entity;
    }

    private PaymentLinkDTO toDto(PaymentLinkEntity entity) {
        PaymentLinkDTO dto = new PaymentLinkDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setSessionId(entity.getSessionId());
        dto.setAmountBtc(entity.getAmountBtc());
        dto.setGrossAmountBtc(entity.getGrossAmountBtc());
        dto.setDepositFeeBtc(entity.getDepositFeeBtc());
        dto.setNetAmountBtc(entity.getNetAmountBtc());
        dto.setDescription(entity.getDescription());
        dto.setDepositAddress(entity.getDepositAddress());
        dto.setVisibility(entity.getVisibility());
        dto.setConfirmationMode(entity.getConfirmationMode());
        dto.setAmountLocked(entity.getAmountLocked());
        dto.setReferenceLabel(entity.getReferenceLabel());
        dto.setMetadata(readMetadata(entity.getMetadataJson()));
        dto.setStatus(entity.getStatus());
        dto.setTxid(entity.getTxid());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setPaidAt(entity.getPaidAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCancelledAt(entity.getCancelledAt());
        dto.setCancelReason(entity.getCancelReason());
        return dto;
    }

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid payment link metadata.", exception);
        }
    }

    private Map<String, String> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, METADATA_TYPE);
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
